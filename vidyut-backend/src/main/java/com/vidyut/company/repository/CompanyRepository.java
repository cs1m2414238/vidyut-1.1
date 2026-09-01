package com.vidyut.company.repository;

import com.vidyut.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByAccount_Id(Long accountId);
    Optional<Company> findByCompanyName(String companyName);
    boolean existsByCompanyName(String companyName);
    boolean existsByCompanyNameAndAccount_IdNot(String companyName, Long accountId);
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumberAndAccount_IdNot(String registrationNumber, Long accountId);
}
