package fr.medislot.document;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class IcsWriter {
    private static final DateTimeFormatter ICS_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String write(Instant startsAt, Instant endsAt, Long appointmentId) {
        return String.join("\r\n",
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//MediSlot//FR",
            "BEGIN:VEVENT",
            "UID:appointment-" + appointmentId + "@medislot.fr",
            "DTSTAMP:" + ICS_DATE.format(Instant.now()),
            "DTSTART:" + ICS_DATE.format(startsAt),
            "DTEND:" + ICS_DATE.format(endsAt),
            "SUMMARY:Rendez-vous Médecin",
            "END:VEVENT",
            "END:VCALENDAR",
            "");
    }
}
