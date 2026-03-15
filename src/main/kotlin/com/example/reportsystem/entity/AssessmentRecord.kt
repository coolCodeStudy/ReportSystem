package com.example.reportsystem.entity

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.*

import org.hibernate.annotations.Where

@Entity
@Table(name = "assessment_record")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
@Where(clause = "is_deleted = false")
class AssessmentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: Student? = null

    @Column(name = "assessment_type")
    var assessmentType: String? = null

    @Column(name = "other_assessment")
    var otherAssessment: String? = null

    @Column(name = "target_grade")
    var targetGrade: String? = null

    @Column(name = "lingoland_level")
    var lingolandLevel: String? = null

    @Column(name = "study_goal")
    var studyGoal: String? = null

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb", name = "assessment_results")
    var assessmentResults: String? = null

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(name = "assessment_date")
    var assessmentDate: LocalDate? = null

    @Column(name = "is_deleted")
    var isDeleted: Boolean = false

    @PrePersist
    fun prePersist() {
        createdAt = LocalDateTime.now()
    }
}
