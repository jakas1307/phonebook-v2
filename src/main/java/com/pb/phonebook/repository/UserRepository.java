package com.pb.phonebook.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pb.phonebook.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

        Optional<User> findByEmailIgnoreCase(String email);

        Optional<User> findByNameIgnoreCase(String name);

        @Query("SELECT u.id FROM User u ORDER BY u.id ASC")
        List<Long> findAllIdsOrdered();

        // Mengembalikan ID saja, dipaginasi di database. Filter extension memakai EXISTS
        // supaya tiap user muncul sekali (tanpa duplikat dari JOIN extensions).
        @Query(value = "SELECT u.id FROM User u " +
                        "LEFT JOIN u.company c " +
                        "LEFT JOIN u.location l " +
                        "WHERE (:subregion IS NULL OR TRIM(LOWER(l.subregion)) = TRIM(LOWER(:subregion))) " +
                        "AND (:locationId IS NULL OR l.id = :locationId) " +
                        "AND (:keyword IS NULL OR (" +
                        "   LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.subregion) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR EXISTS (SELECT ext.id FROM Extension ext WHERE ext.user = u AND (" +
                        "       LOWER(ext.number) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "       OR LOWER(ext.type) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
                        "))",
                        countQuery = "SELECT COUNT(u.id) FROM User u " +
                        "LEFT JOIN u.company c " +
                        "LEFT JOIN u.location l " +
                        "WHERE (:subregion IS NULL OR TRIM(LOWER(l.subregion)) = TRIM(LOWER(:subregion))) " +
                        "AND (:locationId IS NULL OR l.id = :locationId) " +
                        "AND (:keyword IS NULL OR (" +
                        "   LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR LOWER(l.subregion) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "   OR EXISTS (SELECT ext.id FROM Extension ext WHERE ext.user = u AND (" +
                        "       LOWER(ext.number) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                        "       OR LOWER(ext.type) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
                        "))")
        Page<Long> searchUserIds(
                        @Param("keyword") String keyword,
                        @Param("subregion") String subregion,
                        @Param("locationId") Long locationId,
                        Pageable pageable);

        // Mengambil company, location, dan extensions sekaligus (menghindari N+1).
        @EntityGraph(attributePaths = { "company", "location", "extensions" })
        List<User> findByIdIn(Collection<Long> ids);

        @Query("SELECT u FROM User u " +
                        "JOIN u.company c " +
                        "JOIN u.location l " +
                        "WHERE LOWER(TRIM(u.name)) = LOWER(TRIM(:name)) " +
                        "AND LOWER(TRIM(c.name)) = LOWER(TRIM(:companyName)) " +
                        "AND LOWER(TRIM(l.name)) = LOWER(TRIM(:locationName)) " +
                        "AND LOWER(TRIM(l.subregion)) = LOWER(TRIM(:subregion))")

        Optional<User> findExactDuplicate(
                        @Param("name") String name,
                        @Param("companyName") String companyName,
                        @Param("locationName") String locationName,
                        @Param("subregion") String subregion);
}
