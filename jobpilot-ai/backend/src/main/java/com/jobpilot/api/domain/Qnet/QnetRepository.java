package com.jobpilot.api.domain.Qnet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QnetRepository extends JpaRepository<Qnet, Long> {
}