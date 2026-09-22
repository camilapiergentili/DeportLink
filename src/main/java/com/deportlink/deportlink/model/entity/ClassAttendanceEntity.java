package com.deportlink.deportlink.model.entity;

import com.deportlink.deportlink.enums.ClassAttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "class_attendance",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_class_attendance_session_player", columnNames = {"class_session_id", "player_id"})
    },
    indexes = {
        @Index(name = "idx_class_attendance_player", columnList = "player_id")
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClassAttendanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSessionEntity classSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassAttendanceStatus status;
}
