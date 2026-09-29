package com.zenarahealth.sbt.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Journey {
    private String journeyId;
    private String name;
    private List<Component> components;
}
