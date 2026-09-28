"""Usage guard: stops the API when monthly Lambda usage reaches the free-tier threshold (FR-015, research R7)."""

import datetime
import os

import boto3

cloudwatch = boto3.client("cloudwatch")
lam = boto3.client("lambda")
sns = boto3.client("sns")

API_FUNCTION = os.environ["API_FUNCTION_NAME"]
TOPIC_ARN = os.environ["ALERT_TOPIC_ARN"]
THRESHOLD = float(os.environ.get("THRESHOLD", "0.8"))
REQUEST_LIMIT = 1_000_000  # Lambda Always Free requests per month
GBS_LIMIT = 400_000  # Lambda Always Free GB-seconds per month


def month_sum(metric, dimensions, start, end):
    # GetMetricStatistics is covered by CloudWatch's free API requests; GetMetricData is not.
    points = cloudwatch.get_metric_statistics(
        Namespace="AWS/Lambda", MetricName=metric, Dimensions=dimensions,
        StartTime=start, EndTime=end, Period=86400, Statistics=["Sum"],
    )["Datapoints"]
    return sum(p["Sum"] for p in points)


def usage(now):
    start = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
    requests = month_sum("Invocations", [], start, now)
    gbs = 0.0
    for page in lam.get_paginator("list_functions").paginate():
        for fn in page["Functions"]:
            ms = month_sum("Duration", [{"Name": "FunctionName", "Value": fn["FunctionName"]}], start, now)
            gbs += ms / 1000 * fn["MemorySize"] / 1024
    return requests, gbs


def is_stopped():
    return lam.get_function_concurrency(FunctionName=API_FUNCTION).get("ReservedConcurrentExecutions") == 0


def handler(event, context):
    threshold = float((event or {}).get("thresholdOverride", THRESHOLD))
    requests, gbs = usage(datetime.datetime.now(datetime.timezone.utc))
    ratio = max(requests / REQUEST_LIMIT, gbs / GBS_LIMIT)
    report = f"requests={requests:.0f}/{REQUEST_LIMIT} gb_s={gbs:.0f}/{GBS_LIMIT} usage={ratio:.1%} threshold={threshold:.0%}"
    print(report)
    if ratio < threshold:
        return {"stopped": False, "report": report}
    if is_stopped():
        return {"stopped": True, "report": report, "alreadyStopped": True}
    lam.put_function_concurrency(FunctionName=API_FUNCTION, ReservedConcurrentExecutions=0)
    sns.publish(
        TopicArn=TOPIC_ARN,
        Subject="GerminaWiki API stopped: free-tier usage threshold reached",
        Message=f"The API was stopped automatically to avoid charges.\n{report}\n\n"
                f"Re-enable (see docs/deployment.md): aws lambda delete-function-concurrency --function-name {API_FUNCTION}",
    )
    return {"stopped": True, "report": report}
