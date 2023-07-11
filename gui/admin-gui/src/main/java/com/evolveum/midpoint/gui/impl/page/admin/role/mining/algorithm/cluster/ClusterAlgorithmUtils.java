/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.cluster;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.ExtractIntersections.findPossibleBusinessRole;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.Tools.endTimer;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.Tools.startTimer;
import static com.evolveum.midpoint.model.common.expression.functions.BasicExpressionFunctions.LOGGER;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.IntersectionObject;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.ClusterObjectUtils;

import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.MiningOperationChunk;

import com.evolveum.midpoint.schema.result.OperationResult;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import com.google.common.collect.ListMultimap;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.utils.UUIDToDoubleConverter;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ClusterType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

public class ClusterAlgorithmUtils {

    @NotNull
    public List<PrismObject<ClusterType>> processClusters(PageBase pageBase, List<DataPoint> dataPoints,
            List<Cluster<DataPoint>> clusters, ClusterOptions clusterOptions) {
        long start;
        start = startTimer("generate clusters mp objects");
        List<PrismObject<ClusterType>> clusterTypeObjectWithStatistic = IntStream.range(0, clusters.size())
                .mapToObj(i -> prepareClusters(pageBase, clusters.get(i).getPoints(), String.valueOf(i),
                        dataPoints, clusterOptions))
                .collect(Collectors.toList());
        endTimer(start, "generate clusters mp objects");

        start = startTimer("generate outliers mp objects");
        PrismObject<ClusterType> clusterTypePrismObject = prepareOutlierClusters(pageBase, dataPoints, clusterOptions.getIdentifier());
        clusterTypeObjectWithStatistic.add(clusterTypePrismObject);
        endTimer(start, "generate outliers mp objects");
        return clusterTypeObjectWithStatistic;
    }

    public static List<DataPoint> prepareDataPoints(ListMultimap<List<String>, String> chunkMap) {
        List<DataPoint> dataPoints = new ArrayList<>();

        for (List<String> points : chunkMap.keySet()) {
            List<String> elements = chunkMap.get(points);
            double[] vectorPoints = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                String point = points.get(i);
                vectorPoints[i] = UUIDToDoubleConverter.convertUUid(point);
            }
            dataPoints.add(new DataPoint(vectorPoints, elements, points));
        }
        return dataPoints;
    }

    public ClusterStatistic statisticLoad(List<DataPoint> clusterDataPoints, List<DataPoint> allDataPoints,
            String clusterIndex, String identifier) {

        int totalPoints = 0;
        int minVectorPoint = Integer.MAX_VALUE;
        int maxVectorPoint = -1;
        int clusterGroupSize = clusterDataPoints.size();

        Set<String> elementsOids = new HashSet<>();
        Set<String> occupiedPoints = new HashSet<>();

        for (DataPoint clusterDataPoint : clusterDataPoints) {
            allDataPoints.remove(clusterDataPoint);
            List<String> points = clusterDataPoint.getPoints();
            List<String> elements = clusterDataPoint.getElements();
            elementsOids.addAll(elements);

            int pointsCount = points.size();
            totalPoints += pointsCount;
            minVectorPoint = Math.min(minVectorPoint, pointsCount);
            maxVectorPoint = Math.max(maxVectorPoint, pointsCount);

            occupiedPoints.addAll(points);
        }

        int pointsCount = occupiedPoints.size();

        double meanPoints = (double) totalPoints / clusterGroupSize;

        double density = Math.min((totalPoints / (double) (clusterDataPoints.size() * pointsCount)) * 100, 100);

        PolyStringType name = PolyStringType.fromOrig("cluster_" + clusterIndex);

        return new ClusterStatistic(name, identifier, elementsOids, elementsOids.size(), pointsCount, minVectorPoint,
                maxVectorPoint, clusterGroupSize, meanPoints, density);
    }

    @NotNull
    public List<PrismObject<ClusterType>> processIdenticalGroup(PageBase pageBase, List<DataPoint> dataPoints,
            ClusterOptions clusterOptions) {
        long start;
        start = startTimer("generate clusters mp objects");
        List<DataPoint> dataPointsOutliers = new ArrayList<>();
        List<PrismObject<ClusterType>> clusterTypeObjectWithStatistic = IntStream.range(0, dataPoints.size())
                .mapToObj(i -> prepareIdenticalGroup(pageBase, dataPoints.get(i), String.valueOf(i), clusterOptions, dataPointsOutliers))
                .filter(Objects::nonNull) // Filter out null elements
                .collect(Collectors.toList());

        endTimer(start, "generate clusters mp objects");

        start = startTimer("generate outliers mp objects");
        PrismObject<ClusterType> clusterTypePrismObject = prepareOutlierClusters(pageBase, dataPoints, clusterOptions.getIdentifier());
        clusterTypeObjectWithStatistic.add(clusterTypePrismObject);
        endTimer(start, "generate outliers mp objects");
        return clusterTypeObjectWithStatistic;
    }

    public ClusterStatistic statisticIdenticalLoad(DataPoint clusterDataPoints,
            String clusterIndex, String identifier, int minGroupSize, List<DataPoint> dataPointsOutliers) {

        int totalPoints = 0;
        int minVectorPoint;
        int maxVectorPoint;
        int clusterGroupSize = clusterDataPoints.getElements().size();

        Set<String> elementsOids = new HashSet<>(clusterDataPoints.getElements());
        Set<String> occupiedPoints = new HashSet<>(clusterDataPoints.getPoints());

        if (elementsOids.size() < minGroupSize) {
            dataPointsOutliers.add(clusterDataPoints);
            return null;
        }

        int elementsCount = elementsOids.size();
        int pointsCount = occupiedPoints.size();
        totalPoints += pointsCount * elementsCount;
        minVectorPoint = pointsCount;
        maxVectorPoint = pointsCount;

        double density = Math.min((totalPoints / (double) (elementsCount * pointsCount)) * 100, 100);

        PolyStringType name = PolyStringType.fromOrig("cluster_" + clusterIndex);

        return new ClusterStatistic(name, identifier, elementsOids, elementsCount, pointsCount, minVectorPoint,
                maxVectorPoint, clusterGroupSize, pointsCount, density);
    }

    public PrismObject<ClusterType> prepareIdenticalGroup(PageBase pageBase, DataPoint dataPointCluster, String clusterIndex, ClusterOptions clusterOptions, List<DataPoint> dataPointsOutliers) {

        int minGroupSize = clusterOptions.getMinGroupSize();
        ClusterStatistic clusterStatistic = statisticIdenticalLoad(dataPointCluster, clusterIndex, clusterOptions.getIdentifier(), minGroupSize, dataPointsOutliers);

        if (clusterStatistic != null) {
            return generateClusterObject(pageBase, clusterStatistic, clusterOptions);
        } else {return null;}
    }

    public PrismObject<ClusterType> prepareClusters(PageBase pageBase, List<DataPoint> dataPointCluster, String clusterIndex,
            List<DataPoint> dataPoints, ClusterOptions clusterOptions) {

        ClusterStatistic clusterStatistic = statisticLoad(dataPointCluster, dataPoints, clusterIndex, clusterOptions.getIdentifier());

        return generateClusterObject(pageBase, clusterStatistic, clusterOptions);
    }

    public PrismObject<ClusterType> prepareOutlierClusters(PageBase pageBase, List<DataPoint> dataPoints,
            String identifier) {

        int minVectorPoint = Integer.MAX_VALUE;
        int maxVectorPoint = -1;

        int totalDataPoints = dataPoints.size();
        int sumPoints = 0;

        Set<String> elementsOid = new HashSet<>();
        Set<String> pointsSet = new HashSet<>();
        for (DataPoint dataPoint : dataPoints) {
            List<String> points = dataPoint.getPoints();
            pointsSet.addAll(points);
            List<String> elements = dataPoint.getElements();
            elementsOid.addAll(elements);

            int pointsSize = points.size();
            sumPoints += pointsSize;
            minVectorPoint = Math.min(minVectorPoint, pointsSize);
            maxVectorPoint = Math.max(maxVectorPoint, pointsSize);
        }

        double meanPoints = (double) sumPoints / totalDataPoints;

        double density = (sumPoints / (double) (dataPoints.size() * pointsSet.size())) * 100;

        PolyStringType name = PolyStringType.fromOrig("outliers");

        ClusterStatistic clusterStatistic = new ClusterStatistic(name, identifier, elementsOid, elementsOid.size(),
                sumPoints, minVectorPoint, maxVectorPoint, dataPoints.size(), meanPoints, density);

        return generateClusterObject(pageBase, clusterStatistic, null);
    }

    private @NotNull PrismObject<ClusterType> generateClusterObject(PageBase pageBase, ClusterStatistic clusterStatistic,
            ClusterOptions clusterOptions) {

        PrismObject<ClusterType> clusterTypePrismObject = null;
        try {
            clusterTypePrismObject = pageBase.getPrismContext()
                    .getSchemaRegistry().findObjectDefinitionByCompileTimeClass(ClusterType.class).instantiate();
        } catch (SchemaException e) {
            LOGGER.error("Error while finding object definition by compile time class ClusterType object: {}", e.getMessage(), e);
        }
        assert clusterTypePrismObject != null;

        Set<String> occupiedUsers = clusterStatistic.getElementsOid();

        ClusterType clusterType = clusterTypePrismObject.asObjectable();
        clusterType.setOid(String.valueOf(UUID.randomUUID()));
        clusterType.setElementCount(clusterStatistic.getTotalElements());
        clusterType.getElements().addAll(occupiedUsers);
        clusterType.setPointCount(clusterStatistic.getTotalPoints());
        clusterType.setMean(String.format("%.3f", clusterStatistic.getMeanPoints()));
        clusterType.setDensity(String.format("%.3f", clusterStatistic.getDensity()));
        clusterType.setMinOccupation(clusterStatistic.getMinVectorPoint());
        clusterType.setMaxOccupation(clusterStatistic.getMaxVectorPoint());
        clusterType.setName(clusterStatistic.getName());
        clusterType.setIdentifier(clusterStatistic.getIdentifier());

        List<String> jsonObjectList = new ArrayList<>();
        List<IntersectionObject> possibleBusinessRole = new ArrayList<>();
        OperationResult operationResult = new OperationResult("Prepare data for intersection");
        resolveDefaultIntersection(pageBase, clusterType, jsonObjectList, possibleBusinessRole,
                operationResult, clusterOptions);

        return clusterTypePrismObject;
    }

    private static void resolveDefaultIntersection(PageBase pageBase, ClusterType clusterType,
            List<String> jsonObjectList, List<IntersectionObject> possibleBusinessRole,
            OperationResult operationResult, ClusterOptions clusterOptions) {

        if (clusterOptions != null) {
            ClusterObjectUtils.Mode mode = clusterOptions.getMode();
            int group = Math.min(clusterOptions.getDefaultOccupancySearch(), clusterOptions.getMinGroupSize());
            int intersection = Math.min(clusterOptions.getDefaultIntersectionSearch(), clusterOptions.getMinIntersections());
            double defaultMaxFrequency = clusterOptions.getDefaultMaxFrequency();
            double defaultMinFrequency = clusterOptions.getDefaultMinFrequency();

            if (mode.equals(ClusterObjectUtils.Mode.ROLE)) {
                MiningOperationChunk miningOperationChunk = new MiningOperationChunk(clusterType, pageBase,
                        ClusterObjectUtils.Mode.ROLE, operationResult, true, false);
                possibleBusinessRole = findPossibleBusinessRole(miningOperationChunk, defaultMinFrequency,
                        defaultMaxFrequency,
                        group, intersection, mode);
            } else if (mode.equals(ClusterObjectUtils.Mode.USER)) {
                MiningOperationChunk miningOperationChunk = new MiningOperationChunk(clusterType, pageBase,
                        ClusterObjectUtils.Mode.USER, operationResult, true, false);
                possibleBusinessRole = findPossibleBusinessRole(miningOperationChunk, defaultMinFrequency,
                        defaultMaxFrequency,
                        intersection, group, mode);
            }

            loadIntersections(jsonObjectList, possibleBusinessRole);

            clusterType.getDefaultDetection().addAll(jsonObjectList);
        }
    }

    private static void loadIntersections(List<String> jsonObjectList, List<IntersectionObject> possibleBusinessRole) {
        for (IntersectionObject intersectionObject : possibleBusinessRole) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("points", new JSONArray(intersectionObject.getPoints()));
            jsonObject.put("type", intersectionObject.getType());
            jsonObject.put("currentElements", intersectionObject.getCurrentElements());
            jsonObject.put("totalElements", intersectionObject.getTotalElements());
            jsonObject.put("metric", intersectionObject.getMetric());
            jsonObject.put("elements", new JSONArray(intersectionObject.getElements()));
            jsonObjectList.add(String.valueOf(jsonObject));
        }
    }

    public static List<IntersectionObject> loadDefaultIntersection(ClusterType clusterType) {
        List<String> defaultDetection = clusterType.getDefaultDetection();
        List<IntersectionObject> mergedIntersection = new ArrayList<>();
        for (String jsonString : defaultDetection) {
            JSONObject jsonObject = new JSONObject(jsonString);

            JSONArray pointsArray = jsonObject.getJSONArray("points");
            String type = jsonObject.getString("type");
            int currentElements = jsonObject.getInt("currentElements");
            Integer totalElements = jsonObject.optInt("totalElements");
            double metric = jsonObject.getDouble("metric");
            JSONArray elementsArray = jsonObject.getJSONArray("elements");

            Set<String> points = new HashSet<>();
            for (int i = 0; i < pointsArray.length(); i++) {
                points.add(pointsArray.getString(i));
            }

            Set<String> elements = new HashSet<>();
            for (int i = 0; i < elementsArray.length(); i++) {
                elements.add(elementsArray.getString(i));
            }
            mergedIntersection.add(new IntersectionObject(points, metric, type, currentElements, totalElements, elements));
        }

        return mergedIntersection;
    }
}
