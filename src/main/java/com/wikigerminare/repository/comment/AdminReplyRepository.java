package com.wikigerminare.repository.comment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wikigerminare.entity.comment.AdminReply;

public interface AdminReplyRepository extends JpaRepository<AdminReply, UUID> {
}
