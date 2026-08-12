package com.jobpilot.api.domain.matching.repository;

import com.jobpilot.api.domain.matching.entity.MemberJobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJobEventRepository extends JpaRepository<MemberJobEvent, Long> {}
