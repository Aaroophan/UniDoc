package com.unidoc.demo;

import com.unidoc.request.DeliveryMethod;
import com.unidoc.request.RequestStatus;

import java.util.List;

/** Stable, wholly synthetic local demonstration fixtures; no external identities. */
public final class DemoSeedFixtures {
    private DemoSeedFixtures() { }

    public static final String ADMIN_EMAIL = "admin.demo@unidoc.test";
    public static final String STAFF_EMAIL = "staff.demo@unidoc.test";
    public static final String STUDENT_EMAIL = "student.demo@unidoc.test";
    public static final String SECOND_STUDENT_EMAIL = "student2.demo@unidoc.test";
    // Deliberately documented local-only password; never used for any real account.
    public static final String LOCAL_DEMO_PASSWORD = "UniDocDemo#2026";

    public record Type(String code, String name, String description, String requirements,
                       boolean supportRequired, String location, String instructions) { }

    public static final List<Type> TYPES = List.of(
            new Type("STUDENT_CONFIRMATION", "Student Confirmation Letter",
                    "Confirms the student's current enrolment with the university.",
                    "Provide the purpose of the confirmation and the date it is needed.", false,
                    "Student Services Counter, Main Administration Building",
                    "Bring your student ID and quote your UniDoc request number."),
            new Type("ACADEMIC_TRANSCRIPT", "Academic Transcript",
                    "Provides a formal record of completed courses and academic results.",
                    "State the recipient and intended use. Supporting evidence may be attached.", true,
                    "Academic Records Office, Main Administration Building",
                    "Bring your student ID. Collect during advertised office hours."),
            new Type("RECOMMENDATION_LETTER", "Recommendation Letter",
                    "Requests a formal academic recommendation for an external purpose.",
                    "Explain the programme or opportunity and attach relevant supporting evidence.", true,
                    "Student Services Counter, Main Administration Building",
                    "Bring your student ID and verify the document details before leaving."),
            new Type("CERTIFICATE", "Certificate",
                    "Requests an available certificate confirming a university achievement.",
                    "Specify the certificate and provide the intended purpose of use.", false,
                    "Examinations Office, Main Administration Building",
                    "Bring your student ID and the collection-ready notification."),
            new Type("STUDENT_STATUS_LETTER", "Student Status Letter",
                    "Confirms the student's present academic standing and registration status.",
                    "State where the letter will be submitted and why it is required.", false,
                    "Student Services Counter, Main Administration Building",
                    "Bring your student ID; the document is released only when marked ready.")
    );

    public record RequestSample(String number, String typeCode, boolean secondStudent,
                                DeliveryMethod method, RequestStatus status, String purpose) { }

    public static final List<RequestSample> REQUESTS = List.of(
            new RequestSample("UDR-DEMO-0001", "STUDENT_CONFIRMATION", false,
                    DeliveryMethod.ELECTRONIC, RequestStatus.SUBMITTED, "Confirmation needed for a synthetic scholarship application."),
            new RequestSample("UDR-DEMO-0002", "ACADEMIC_TRANSCRIPT", true,
                    DeliveryMethod.PHYSICAL, RequestStatus.UNDER_REVIEW, "Printed transcript for a demonstration application."),
            new RequestSample("UDR-DEMO-0003", "RECOMMENDATION_LETTER", false,
                    DeliveryMethod.ELECTRONIC, RequestStatus.ADDITIONAL_INFORMATION_REQUIRED,
                    "Recommendation for a fictional research placement."),
            new RequestSample("UDR-DEMO-0004", "CERTIFICATE", true,
                    DeliveryMethod.PHYSICAL, RequestStatus.APPROVED, "Certificate requested for a demonstration employer."),
            new RequestSample("UDR-DEMO-0005", "STUDENT_STATUS_LETTER", false,
                    DeliveryMethod.ELECTRONIC, RequestStatus.PROCESSING, "Status letter needed for a fictional placement."),
            new RequestSample("UDR-DEMO-0006", "ACADEMIC_TRANSCRIPT", true,
                    DeliveryMethod.ELECTRONIC, RequestStatus.READY, "Electronic transcript ready for the demonstration."),
            new RequestSample("UDR-DEMO-0007", "STUDENT_CONFIRMATION", false,
                    DeliveryMethod.PHYSICAL, RequestStatus.READY, "Collectable physical confirmation for the demonstration."),
            new RequestSample("UDR-DEMO-0008", "RECOMMENDATION_LETTER", true,
                    DeliveryMethod.ELECTRONIC, RequestStatus.REJECTED, "Example of a terminal review decision."),
            new RequestSample("UDR-DEMO-0009", "CERTIFICATE", false,
                    DeliveryMethod.ELECTRONIC, RequestStatus.COMPLETED, "Example electronically delivered certificate."),
            new RequestSample("UDR-DEMO-0010", "STUDENT_STATUS_LETTER", true,
                    DeliveryMethod.PHYSICAL, RequestStatus.COMPLETED, "Example of a collected physical status letter.")
    );
}
