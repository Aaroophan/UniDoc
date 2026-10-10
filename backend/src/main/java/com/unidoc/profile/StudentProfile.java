package com.unidoc.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.unidoc.common.persistence.AuditableEntity;
import com.unidoc.common.persistence.Normalization;
import com.unidoc.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;

@Entity
@Table(
        name = "student_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_student_profiles_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uq_student_profiles_registration", columnNames = "registration_number")
        },
        indexes = @Index(name = "ix_student_profiles_department", columnList = "department"),
        check = @CheckConstraint(
                name = "ck_student_profiles_academic_year",
                constraint = "academic_year BETWEEN 1 AND 8"
        )
)
public class StudentProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_student_profiles_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 100)
    private String faculty;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(name = "degree_programme", nullable = false, length = 150)
    private String degreeProgramme;

    @Column(name = "academic_year", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int academicYear;

    protected StudentProfile() {
    }

    public StudentProfile(
            User user,
            String registrationNumber,
            String phone,
            String faculty,
            String department,
            String degreeProgramme,
            int academicYear
    ) {
        this.user = Objects.requireNonNull(user);
        this.registrationNumber = Normalization.registrationNumber(registrationNumber);
        this.phone = phone == null ? null : phone.trim();
        this.faculty = Objects.requireNonNull(faculty).trim();
        this.department = Objects.requireNonNull(department).trim();
        this.degreeProgramme = Objects.requireNonNull(degreeProgramme).trim();
        this.academicYear = academicYear;
    }

    @PrePersist
    @PreUpdate
    void normalizeRegistrationNumber() {
        registrationNumber = Normalization.registrationNumber(registrationNumber);
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public User getUser() {
        return user;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getPhone() {
        return phone;
    }

    public String getFaculty() {
        return faculty;
    }

    public String getDepartment() {
        return department;
    }

    public String getDegreeProgramme() {
        return degreeProgramme;
    }

    public int getAcademicYear() {
        return academicYear;
    }
}
