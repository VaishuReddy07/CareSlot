package com.careslot.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "careslot.demo-mode", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private static final UUID PRACTITIONER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("""
            INSERT INTO practitioners(id, display_name, specialty, timezone)
            VALUES (?, 'Dr. Élodie Laurent', 'Cardiology', 'Europe/Paris')
            ON CONFLICT (id) DO NOTHING
            """, PRACTITIONER_ID);

        String[][] people = {
            {"10000000-0000-0000-0000-000000000001", "demo:sophie-laurent", "Sophie Laurent"},
            {"10000000-0000-0000-0000-000000000002", "demo:marc-dubois", "Marc Dubois"},
            {"10000000-0000-0000-0000-000000000003", "demo:amelie-moreau", "Amélie Moreau"},
            {"10000000-0000-0000-0000-000000000004", "demo:thomas-bernard", "Thomas Bernard"}
        };
        for (String[] person : people) {
            jdbc.update("""
                INSERT INTO patients(id, subject_id, display_name) VALUES (?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, UUID.fromString(person[0]), person[1], person[2]);
        }

        List<Object[]> hours = List.of(
            new Object[]{1, LocalTime.of(9, 0), LocalTime.NOON}, new Object[]{1, LocalTime.of(13, 0), LocalTime.of(17, 0)},
            new Object[]{2, LocalTime.of(9, 0), LocalTime.NOON}, new Object[]{2, LocalTime.of(13, 0), LocalTime.of(17, 0)},
            new Object[]{3, LocalTime.of(9, 0), LocalTime.NOON}, new Object[]{3, LocalTime.of(13, 0), LocalTime.of(17, 0)},
            new Object[]{4, LocalTime.of(9, 0), LocalTime.NOON}, new Object[]{4, LocalTime.of(13, 0), LocalTime.of(17, 0)},
            new Object[]{5, LocalTime.of(9, 0), LocalTime.NOON}, new Object[]{5, LocalTime.of(13, 0), LocalTime.of(17, 0)},
            new Object[]{6, LocalTime.of(9, 0), LocalTime.NOON});
        jdbc.batchUpdate("""
            INSERT INTO practitioner_availability(practitioner_id, day_of_week, starts_at, ends_at)
            VALUES (?, ?, ?, ?) ON CONFLICT (practitioner_id, day_of_week, starts_at, ends_at) DO NOTHING
            """, hours, hours.size(), (ps, hour) -> {
            ps.setObject(1, PRACTITIONER_ID);
            ps.setInt(2, (Integer) hour[0]);
            ps.setObject(3, hour[1]);
            ps.setObject(4, hour[2]);
        });

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
        String[][] visits = {
            {"20000000-0000-0000-0000-000000000001", "10000000-0000-0000-0000-000000000001", "09:00", "30", "Follow-up"},
            {"20000000-0000-0000-0000-000000000002", "10000000-0000-0000-0000-000000000002", "09:30", "45", "New patient"},
            {"20000000-0000-0000-0000-000000000003", "10000000-0000-0000-0000-000000000003", "10:15", "30", "Consultation"},
            {"20000000-0000-0000-0000-000000000004", "10000000-0000-0000-0000-000000000004", "11:00", "45", "Follow-up"}
        };
        for (String[] visit : visits) {
            jdbc.update("""
                INSERT INTO appointments(id, practitioner_id, patient_id, starts_at, ends_at, status, visit_type)
                VALUES (?, ?, ?, ? AT TIME ZONE 'Europe/Paris', (? AT TIME ZONE 'Europe/Paris') + (? || ' minutes')::interval, 'BOOKED', ?)
                ON CONFLICT (id) DO NOTHING
                """, UUID.fromString(visit[0]), PRACTITIONER_ID, UUID.fromString(visit[1]),
                today.atTime(LocalTime.parse(visit[2])), today.atTime(LocalTime.parse(visit[2])), visit[3], visit[4]);
        }
    }
}
