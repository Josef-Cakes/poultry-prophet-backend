package com.poultryprophet.vaccination;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vaccination_program")
public class VaccinationProgram {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "farm_id", nullable = false) private Long farmId;
    @Column(name = "series_id", nullable = false) private UUID seriesId;
    @Column(nullable = false) private String name;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "supersedes_program_id") private Long supersedesProgramId;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "default_for_new_batches", nullable = false) private boolean defaultForNewBatches;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getFarmId(){return farmId;} public void setFarmId(Long v){farmId=v;}
    public UUID getSeriesId(){return seriesId;} public void setSeriesId(UUID v){seriesId=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;} public int getVersionNumber(){return versionNumber;} public void setVersionNumber(int v){versionNumber=v;}
    public Long getSupersedesProgramId(){return supersedesProgramId;} public void setSupersedesProgramId(Long v){supersedesProgramId=v;} public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public boolean isDefaultForNewBatches(){return defaultForNewBatches;} public void setDefaultForNewBatches(boolean v){defaultForNewBatches=v;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public void touch(){updatedAt=Instant.now();}
}
