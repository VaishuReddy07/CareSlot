package com.careslot.appointment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "careslot.demo-mode=true")
@Testcontainers(disabledWithoutDocker = true)
class AppointmentConcurrencyTest {
    private static final UUID PRACTITIONER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("careslot_test")
        .withUsername("careslot")
        .withPassword("careslot_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AppointmentService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwentyFiveConcurrentBookingsCanClaimTheSamePractitionerSlot() throws Exception {
        UUID patientId = UUID.randomUUID();
        jdbc.update("INSERT INTO patients(id, subject_id, display_name) VALUES (?, ?, ?)", patientId, "test:concurrent", "Concurrent Test");
        ZonedDateTime start = nextMondayAtTen();
        var startsAt = start.toInstant();
        var endsAt = start.plusMinutes(30).toInstant();
        int contenders = 25;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch begin = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(contenders)) {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                requests.add(() -> {
                    ready.countDown();
                    begin.await();
                    try {
                        service.book(PRACTITIONER_ID, patientId, startsAt, endsAt, null, "Consultation");
                        return true;
                    } catch (AppointmentConflictException conflict) {
                        return false;
                    }
                });
            }
            var futures = requests.stream().map(pool::submit).toList();
            ready.await();
            begin.countDown();
            long successfulBookings = 0;
            for (var future : futures) if (future.get()) successfulBookings++;
            assertEquals(1, successfulBookings);
        }
        Integer persisted = jdbc.queryForObject(
            "SELECT count(*) FROM appointments WHERE practitioner_id = ? AND starts_at = ? AND status = 'BOOKED'",
            Integer.class, PRACTITIONER_ID, startsAt);
        assertEquals(1, persisted);
    }

    private static ZonedDateTime nextMondayAtTen() {
        LocalDate date = LocalDate.now(PARIS).plusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) date = date.plusDays(1);
        return date.atTime(LocalTime.of(10, 0)).atZone(PARIS);
    }
}

