package com.wikigerminare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.wikigerminare.repository.ImageRepository;
import com.wikigerminare.storage.ObjectStorage;

/**
 * Daily removal of stored files whose image records were deleted (queued by the V2 trigger, including
 * cascades from deleted pages). Idempotent: deleting a missing object is a no-op, and a failed key stays
 * queued for the next run (spec 005, FR-009).
 */
@Service
public class ImageCleanupService {

	static final int BATCH_SIZE = 500;

	private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);

	private final ImageRepository repository;

	private final ObjectStorage storage;

	public ImageCleanupService(ImageRepository repository, ObjectStorage storage) {
		this.repository = repository;
		this.storage = storage;
	}

	/**
	 * @return how many queued objects were removed
	 */
	public int run() {
		int deleted = 0;
		for (String key : repository.queuedObjectKeys(BATCH_SIZE)) {
			try {
				storage.delete(key);
				repository.dequeue(key);
				deleted++;
			}
			catch (RuntimeException ex) {
				log.warn("Image cleanup failed for one object; it stays queued", ex);
			}
		}
		log.info("Image cleanup removed {} object(s)", deleted);
		return deleted;
	}

}
