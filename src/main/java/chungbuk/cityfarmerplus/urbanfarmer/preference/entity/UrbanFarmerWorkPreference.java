package chungbuk.cityfarmerplus.urbanfarmer.preference.entity;

import chungbuk.cityfarmerplus.auth.entity.User;
import chungbuk.cityfarmerplus.common.region.ChungbukCityCounty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
        name = "urban_farmer_work_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_work_preference_user",
                columnNames = "urban_farmer_user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UrbanFarmerWorkPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "urban_farmer_user_id", nullable = false, updatable = false)
    private User urbanFarmer;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "urban_farmer_preference_regions",
            joinColumns = @JoinColumn(name = "work_preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_work_preference_region",
                    columnNames = {"work_preference_id", "city_county"}
            )
    )
    @OrderColumn(name = "display_order")
    @Enumerated(EnumType.STRING)
    @Column(name = "city_county", nullable = false, length = 30)
    private List<ChungbukCityCounty> preferredRegions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "urban_farmer_preference_days",
            joinColumns = @JoinColumn(name = "work_preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_work_preference_day",
                    columnNames = {"work_preference_id", "available_day"}
            )
    )
    @OrderColumn(name = "display_order")
    @Enumerated(EnumType.STRING)
    @Column(name = "available_day", nullable = false, length = 10)
    private List<DayOfWeek> availableDays = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "urban_farmer_preference_work_types",
            joinColumns = @JoinColumn(name = "work_preference_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_work_preference_type",
                    columnNames = {"work_preference_id", "work_type"}
            )
    )
    @OrderColumn(name = "display_order")
    @Column(name = "work_type", nullable = false, length = 50)
    private List<String> availableWorkTypes = new ArrayList<>();

    @Column(name = "preferred_start_date")
    private LocalDate preferredStartDate;

    @Column(name = "preferred_end_date")
    private LocalDate preferredEndDate;

    @Column(name = "can_travel", nullable = false)
    private boolean canTravel;

    @Column(length = 1000)
    private String notes;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UrbanFarmerWorkPreference create(
            User urbanFarmer,
            List<ChungbukCityCounty> preferredRegions,
            List<DayOfWeek> availableDays,
            List<String> availableWorkTypes,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            boolean canTravel,
            String notes
    ) {
        if (urbanFarmer.getUserType() != User.UserType.URBAN_FARMER) {
            throw new IllegalArgumentException("도시농부 사용자만 희망 근무 조건을 등록할 수 있습니다.");
        }
        UrbanFarmerWorkPreference preference = new UrbanFarmerWorkPreference();
        preference.urbanFarmer = urbanFarmer;
        preference.change(
                preferredRegions,
                availableDays,
                availableWorkTypes,
                preferredStartDate,
                preferredEndDate,
                canTravel,
                notes
        );
        return preference;
    }

    public void change(
            List<ChungbukCityCounty> preferredRegions,
            List<DayOfWeek> availableDays,
            List<String> availableWorkTypes,
            LocalDate preferredStartDate,
            LocalDate preferredEndDate,
            boolean canTravel,
            String notes
    ) {
        if (preferredRegions == null || preferredRegions.isEmpty()
                || availableDays == null || availableDays.isEmpty()
                || availableWorkTypes == null || availableWorkTypes.isEmpty()) {
            throw new IllegalArgumentException("희망 지역, 요일, 작업 유형은 하나 이상이어야 합니다.");
        }
        validateAvailableWorkTypes(availableWorkTypes);
        if (preferredStartDate == null || preferredEndDate == null) {
            throw new IllegalArgumentException("희망 근무 시작일과 종료일은 필수입니다.");
        }
        if (preferredEndDate.isBefore(preferredStartDate)) {
            throw new IllegalArgumentException("희망 근무 종료일은 시작일보다 빠를 수 없습니다.");
        }
        this.preferredRegions = new ArrayList<>(preferredRegions);
        this.availableDays = new ArrayList<>(availableDays);
        this.availableWorkTypes = new ArrayList<>(availableWorkTypes);
        this.preferredStartDate = preferredStartDate;
        this.preferredEndDate = preferredEndDate;
        this.canTravel = canTravel;
        this.notes = notes;
    }

    private static void validateAvailableWorkTypes(List<String> workTypes) {
        for (String workType : workTypes) {
            if (workType == null || workType.isBlank()) {
                throw new IllegalArgumentException("작업 유형은 빈 값일 수 없습니다.");
            }
            if (workType.length() > 50) {
                throw new IllegalArgumentException("작업 유형은 50자 이하여야 합니다.");
            }
            if (workType.contains(",")
                    || workType.contains("\r")
                    || workType.contains("\n")) {
                throw new IllegalArgumentException(
                        "작업 유형에는 쉼표나 줄바꿈을 사용할 수 없습니다."
                );
            }
        }
    }

    public List<ChungbukCityCounty> getPreferredRegions() {
        return Collections.unmodifiableList(preferredRegions);
    }

    public List<DayOfWeek> getAvailableDays() {
        return Collections.unmodifiableList(availableDays);
    }

    public List<String> getAvailableWorkTypes() {
        return Collections.unmodifiableList(availableWorkTypes);
    }
}
