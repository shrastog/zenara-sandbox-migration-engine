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
public class Component {
    private String id;
    private String name;
    private ComponentType type;
    private String payloadContentHash;
    private List<String> dependencies;
}
