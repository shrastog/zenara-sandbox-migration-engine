package com.zenarahealth.sbt.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Journey {
    private String journeyId;
    private String name;
    private List<Component> components;
}
