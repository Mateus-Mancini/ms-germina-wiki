"""Unit tests for the usage guard (no AWS calls). Run: python3 -m unittest discover -s infra/guard"""
import os
import sys
import types
import unittest
from unittest import mock

os.environ.update(API_FUNCTION_NAME="germinawiki-api", ALERT_TOPIC_ARN="arn:topic", THRESHOLD="0.8")


class FakeClients:
    def __init__(self, invocations, durations_ms, memory, reserved=None):
        self.invocations, self.durations_ms, self.memory, self.reserved = invocations, durations_ms, memory, reserved
        self.put_calls, self.publish_calls, self.metric_calls = [], [], []

    def client(self, name):
        me = self
        if name == "cloudwatch":
            def get_metric_statistics(**kw):
                me.metric_calls.append(kw)
                if kw["MetricName"] == "Invocations":
                    return {"Datapoints": [{"Sum": me.invocations}]}
                fn = kw["Dimensions"][0]["Value"]
                return {"Datapoints": [{"Sum": me.durations_ms[fn]}]}
            return types.SimpleNamespace(get_metric_statistics=get_metric_statistics)
        if name == "lambda":
            pag = types.SimpleNamespace(paginate=lambda: [{"Functions": [
                {"FunctionName": f, "MemorySize": m} for f, m in me.memory.items()]}])
            conc = {} if me.reserved is None else {"ReservedConcurrentExecutions": me.reserved}
            return types.SimpleNamespace(
                get_paginator=lambda n: pag,
                get_function_concurrency=lambda **kw: conc,
                put_function_concurrency=lambda **kw: me.put_calls.append(kw),
            )
        return types.SimpleNamespace(publish=lambda **kw: me.publish_calls.append(kw))


def load(fake):
    sys.modules["boto3"] = types.SimpleNamespace(client=fake.client)
    sys.modules.pop("guard", None)
    import guard
    return guard


class GuardTest(unittest.TestCase):
    def test_low_usage_keeps_serving(self):
        fake = FakeClients(10_000, {"germinawiki-api": 3_000_000, "guard": 100_000}, {"germinawiki-api": 2048, "guard": 128})
        result = load(fake).handler({}, None)
        self.assertFalse(result["stopped"])
        self.assertEqual(fake.put_calls, [])
        self.assertTrue(all(c["Period"] == 86400 for c in fake.metric_calls))

    def test_requests_at_80_percent_stop_api_and_email(self):
        fake = FakeClients(800_000, {"germinawiki-api": 0, "guard": 0}, {"germinawiki-api": 2048, "guard": 128})
        result = load(fake).handler({}, None)
        self.assertTrue(result["stopped"])
        self.assertEqual(fake.put_calls, [{"FunctionName": "germinawiki-api", "ReservedConcurrentExecutions": 0}])
        self.assertEqual(len(fake.publish_calls), 1)

    def test_gb_seconds_at_80_percent_stop_api(self):
        # 160,000,000 ms at 2 GB = 320,000 GB-s = 80%
        fake = FakeClients(1_000, {"germinawiki-api": 160_000_000, "guard": 0}, {"germinawiki-api": 2048, "guard": 128})
        self.assertTrue(load(fake).handler({}, None)["stopped"])

    def test_already_stopped_does_not_email_again(self):
        fake = FakeClients(900_000, {"germinawiki-api": 0, "guard": 0}, {"germinawiki-api": 2048, "guard": 128}, reserved=0)
        result = load(fake).handler({}, None)
        self.assertTrue(result["alreadyStopped"])
        self.assertEqual(fake.put_calls, [])
        self.assertEqual(fake.publish_calls, [])

    def test_threshold_override_for_drills(self):
        fake = FakeClients(1, {"germinawiki-api": 0, "guard": 0}, {"germinawiki-api": 2048, "guard": 128})
        self.assertTrue(load(fake).handler({"thresholdOverride": 0}, None)["stopped"])


if __name__ == "__main__":
    unittest.main(verbosity=1)
