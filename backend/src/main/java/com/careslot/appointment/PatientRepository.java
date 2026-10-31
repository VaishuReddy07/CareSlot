package com.careslot.appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PatientRepository extends JpaRepository<Patient, UUID> {}
