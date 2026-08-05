package com.jobpilot.api.domain.locationjobs.matching.repository;

import com.jobpilot.api.domain.locationjobs.matching.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LocationJobRepository extends JpaRepository<Location, Long> {

    @Query(value = """
        SELECT *,
            (6371 * acos(
                cos(radians(:centerLat)) * cos(radians(latitude)) *
                cos(radians(longitude) - radians(:centerLng)) +
                sin(radians(:centerLat)) * sin(radians(latitude))
            )) AS distance_km
        FROM job_posting_locations
        HAVING distance_km <= :radiusKm
        ORDER BY distance_km ASC
        """, nativeQuery = true)
    List<Location> findJobsWithinRadius(
            @Param("centerLat") Double centerLat,
            @Param("centerLng") Double centerLng,
            @Param("radiusKm") Double radiusKm
    );
}