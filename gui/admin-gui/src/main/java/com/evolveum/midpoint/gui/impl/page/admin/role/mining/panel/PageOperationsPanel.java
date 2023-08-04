/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.admin.role.mining.panel;

import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.utils.ClusterAlgorithmUtils.transformDefaultPattern;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.ClusterObjectUtils.*;
import static com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.Tools.*;
import static com.evolveum.midpoint.web.component.data.column.ColumnUtils.createStringResource;

import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.detection.DetectedPattern;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.detection.DetectionAction;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.algorithm.object.DetectionOption;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.MiningOperationChunk;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.MiningRoleTypeChunk;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.objects.MiningUserTypeChunk;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.panel.details.objects.ExecuteSearchPanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.panel.details.objects.ProcessBusinessRolePanel;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.MiningIntersectionTable;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.MiningRoleBasedTable;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.tables.MiningUserBasedTable;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.ClusterObjectUtils;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.PrepareChunkStructure;
import com.evolveum.midpoint.gui.impl.page.admin.role.mining.utils.PrepareExpandStructure;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.web.component.AjaxButton;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisClusterType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisDetectionModeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisProcessModeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleAnalysisSessionType;

public class PageOperationsPanel extends Panel {

    private static final String ID_DATATABLE = "datatable_extra";
    private static final String ID_DATATABLE_INTERSECTIONS = "table_intersection";
    private static final String ID_PROCESS_BUTTON = "process_selections_id";

    double minFrequency = 0.3;
    Integer minOccupancy = 5;
    double maxFrequency = 1.0;
    Integer minIntersection = 10;

    RoleAnalysisDetectionModeType searchMode = RoleAnalysisDetectionModeType.INTERSECTION;
    RoleAnalysisProcessModeType processMode;

    DetectionOption detectionOption;

    List<DetectedPattern> detectedPatternList = new ArrayList<>();
    AjaxButton processButton;
    DetectedPattern intersection = null;
    boolean compress = true;

    String compressMode = "COMPRESS MODE";

    OperationResult result = new OperationResult("GetObject");

    MiningOperationChunk miningOperationChunk;

    List<MiningRoleTypeChunk> miningRoleTypeChunks = new ArrayList<>();
    List<MiningUserTypeChunk> miningUserTypeChunks = new ArrayList<>();
    ClusterObjectUtils.SORT sortMode;

    String sessionOid;
    String clusterOid;

    public PageOperationsPanel(String id, String sessionOid, String clusterOid) {
        super(id);
        this.sessionOid = sessionOid;
        this.clusterOid = clusterOid;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        initOperationPart();

    }

    private void initOperationPart() {
        miningRoleTypeChunks = new ArrayList<>();
        miningUserTypeChunks = new ArrayList<>();

        RoleAnalysisClusterType cluster = getClusterTypeObject((PageBase) getPage(), clusterOid).asObjectable();

        PrismObject<RoleAnalysisSessionType> getParent = getParentClusterByOid((PageBase) getPage(),
                sessionOid, new OperationResult("getParent"));
        assert getParent != null;
        String processModeValue = getParent.asObjectable().getClusterOptions().getProcessMode().value();
        String searchModeValue = cluster.getDetectionOption().getDetectionMode().value();
        searchMode = RoleAnalysisDetectionModeType.fromValue(searchModeValue);
        processMode = RoleAnalysisProcessModeType.fromValue(processModeValue);

        Integer elementsCount = cluster.getClusterStatistic().getMemberCount();
        Integer pointsCount = cluster.getClusterStatistic().getPropertiesCount();
        int max = Math.max(elementsCount, pointsCount);

        if (max <= 500) {
            sortMode = SORT.JACCARD;
        } else {
            sortMode = SORT.NONE;
        }
        if (cluster.getDetectionOption() != null) {
            detectionOption = loadDetectionOption(cluster.getDetectionOption());
        } else {
            detectionOption = new DetectionOption(0.3, 1, 5, 10, searchMode, 0.8);
        }

        searchMode = detectionOption.getSearchMode();
        minFrequency = detectionOption.getMinFrequencyThreshold();
        minOccupancy = detectionOption.getMinOccupancy();
        maxFrequency = detectionOption.getMaxFrequencyThreshold();
        minIntersection = detectionOption.getMinPropertiesOverlap();

        long start = startTimer("LOAD DATA");
        detectedPatternList = transformDefaultPattern(cluster);
        loadMiningTableData(sortMode);
        endTimer(start, "LOAD DATA");

        start = startTimer("LOAD TABLE");
        loadMiningTable(miningRoleTypeChunks, miningUserTypeChunks, detectionOption.getSearchMode());
        endTimer(start, "LOAD TABLE");

        add(generateTableIntersection(ID_DATATABLE_INTERSECTIONS, detectedPatternList).setOutputMarkupId(true));

        AjaxButton ajaxButton = executeBusinessSearchPanel();
        add(ajaxButton);

        AjaxButton sortButton = executeJaccardSorting();
        add(sortButton);

        initProcessButton();

        add(processButton);
    }

    private void initProcessButton() {
        processButton = new AjaxButton(ID_PROCESS_BUTTON, createStringResource("RoleMining.button.title.process")) {
            @Override
            public void onClick(AjaxRequestTarget ajaxRequestTarget) {

                ProcessBusinessRolePanel detailsPanel = new ProcessBusinessRolePanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("TO DO: details"), miningRoleTypeChunks, miningUserTypeChunks, processMode) {
                    @Override
                    public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                        super.onClose(ajaxRequestTarget);
                    }
                };
                ((PageBase) getPage()).showMainPopup(detailsPanel, ajaxRequestTarget);

            }

        };

        processButton.setOutputMarkupId(true);
        processButton.setOutputMarkupPlaceholderTag(true);
        processButton.setVisible(false);
    }

    @NotNull
    private AjaxButton executeBusinessSearchPanel() {
        AjaxButton ajaxButton = new AjaxButton("business_role_mining") {
            @Override
            public void onClick(AjaxRequestTarget target) {

                ExecuteSearchPanel detailsPanel = new ExecuteSearchPanel(((PageBase) getPage()).getMainPopupBodyId(),
                        Model.of("Analyzed members details panel"), processMode, detectionOption) {
                    @Override
                    public void performAction(AjaxRequestTarget target, DetectionOption newDetectionOption) {
                        intersection = null;

                        detectedPatternList = new DetectionAction(newDetectionOption).executeDetection(miningRoleTypeChunks,
                                miningUserTypeChunks, processMode);

                        searchMode = newDetectionOption.getSearchMode();
                        minFrequency = newDetectionOption.getMinFrequencyThreshold();
                        maxFrequency = newDetectionOption.getMaxFrequencyThreshold();
                        minIntersection = newDetectionOption.getMinPropertiesOverlap();
                        minOccupancy = newDetectionOption.getMinOccupancy();
                        detectionOption = newDetectionOption;
                        getIntersectionTable().replaceWith(generateTableIntersection(ID_DATATABLE_INTERSECTIONS,
                                detectedPatternList));
                        target.add(getIntersectionTable().setOutputMarkupId(true));

                        updateMiningTable(target, true, searchMode, miningRoleTypeChunks, miningUserTypeChunks);

//                        replaceRoleAnalysisClusterDetectionOption(getPageParameterChildOid(),(PageBase) getPage(),newDetectionOption,result);
                        replaceRoleAnalysisClusterDetection(clusterOid, (PageBase) getPage(), result,
                                detectedPatternList, processMode, newDetectionOption);
                    }

                    @Override
                    public void onClose(AjaxRequestTarget ajaxRequestTarget) {
                        super.onClose(ajaxRequestTarget);
                    }
                };
                ((PageBase) getPage()).showMainPopup(detailsPanel, target);
            }
        };
        ajaxButton.setOutputMarkupId(true);
        return ajaxButton;
    }

    private AjaxButton executeJaccardSorting() {

        AjaxButton ajaxButton = new AjaxButton("jaccard_sort") {

            @Override
            public void onClick(AjaxRequestTarget target) {
                sortMode = ClusterObjectUtils.SORT.JACCARD;

                miningRoleTypeChunks = miningOperationChunk.getMiningRoleTypeChunks(sortMode);
                miningUserTypeChunks = miningOperationChunk.getMiningUserTypeChunks(sortMode);

                updateMiningTable(target, true, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
                target.add(this.setVisible(false));
            }
        };

        ajaxButton.setOutputMarkupId(true);
        ajaxButton.setVisible(sortMode.equals(ClusterObjectUtils.SORT.NONE));
        return ajaxButton;
    }

    String state = "START";

    private void loadMiningTableData(SORT sortMode) {
        RoleAnalysisClusterType cluster = getClusterTypeObject((PageBase) getPage(), clusterOid).asObjectable();

        //TODO should only be used on a gui request?
        // In the case of large datasets, Jaccard sorting is
        // time-consuming. Or progress (loading) bar?

        if (compress) {

            miningOperationChunk = new PrepareChunkStructure().executeOperation(cluster, true,
                    processMode, (PageBase) getPage(), new OperationResult("loadData"), state);

        } else {
            miningOperationChunk = new PrepareExpandStructure().executeOperation(cluster, true,
                    processMode, (PageBase) getPage(), new OperationResult("loadData"), state);
        }
        this.miningRoleTypeChunks = miningOperationChunk.getMiningRoleTypeChunks(sortMode);
        this.miningUserTypeChunks = miningOperationChunk.getMiningUserTypeChunks(sortMode);

    }

    private void loadMiningTable(List<MiningRoleTypeChunk> miningRoleTypeChunks, List<MiningUserTypeChunk> miningUserTypeChunks,
            RoleAnalysisDetectionModeType searchMode) {
        if (processMode.equals(RoleAnalysisProcessModeType.ROLE)) {
            MiningRoleBasedTable boxedTablePanel = generateMiningRoleBasedTable(miningRoleTypeChunks,
                    miningUserTypeChunks, false, minFrequency, null, maxFrequency, searchMode);
            boxedTablePanel.setOutputMarkupId(true);
            add(boxedTablePanel);
        } else if (processMode.equals(RoleAnalysisProcessModeType.USER)) {
            MiningUserBasedTable boxedTablePanel = generateMiningUserBasedTable(miningRoleTypeChunks,
                    miningUserTypeChunks, false, minFrequency, null, maxFrequency);
            boxedTablePanel.setOutputMarkupId(true);
            add(boxedTablePanel);
        }

    }

    private void updateMiningTable(AjaxRequestTarget target, boolean resetStatus, RoleAnalysisDetectionModeType searchMode,
            List<MiningRoleTypeChunk> miningRoleTypeChunks, List<MiningUserTypeChunk> miningUserTypeChunks) {

        if (resetStatus) {
            for (MiningRoleTypeChunk miningRoleTypeChunk : miningRoleTypeChunks) {
                miningRoleTypeChunk.setStatus(ClusterObjectUtils.Status.NEUTRAL);
            }
            for (MiningUserTypeChunk miningUserTypeChunk : miningUserTypeChunks) {
                miningUserTypeChunk.setStatus(ClusterObjectUtils.Status.NEUTRAL);
            }
        }

        if (processMode.equals(RoleAnalysisProcessModeType.ROLE)) {
            MiningRoleBasedTable boxedTablePanel = generateMiningRoleBasedTable(miningRoleTypeChunks,
                    miningUserTypeChunks, false, minFrequency, intersection, maxFrequency, searchMode);
            boxedTablePanel.setOutputMarkupId(true);
            getMiningRoleBasedTable().replaceWith(boxedTablePanel);
            target.appendJavaScript(getScaleScript());
            target.add(getMiningRoleBasedTable().setOutputMarkupId(true));

        } else if (processMode.equals(RoleAnalysisProcessModeType.USER)) {

            MiningUserBasedTable boxedTablePanel = generateMiningUserBasedTable(miningRoleTypeChunks,
                    miningUserTypeChunks, false, minFrequency, intersection, maxFrequency);
            boxedTablePanel.setOutputMarkupId(true);
            getMiningUserBasedTable().replaceWith(boxedTablePanel);
            target.appendJavaScript(getScaleScript());
            target.add(getMiningUserBasedTable().setOutputMarkupId(true));
        }

    }

    public MiningUserBasedTable generateMiningUserBasedTable(List<MiningRoleTypeChunk> roles,
            List<MiningUserTypeChunk> users, boolean sortable, double frequency, DetectedPattern intersection, double maxFrequency) {
        return new MiningUserBasedTable(ID_DATATABLE, roles, users, sortable, frequency, intersection, maxFrequency, searchMode) {
            @Override
            public void resetTable(AjaxRequestTarget target) {
                updateMiningTable(target, false, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
            }

            @Override
            protected String getCompressStatus() {
                return compressMode;
            }

            @Override
            protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
                if (compress) {
                    compress = false;
                    compressMode = "EXPAND MODE";
                } else {
                    compress = true;
                    compressMode = "COMPRESS MODE";
                }
                loadMiningTableData(sortMode);
                updateMiningTable(ajaxRequestTarget, false, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
                ajaxRequestTarget.add(this);
            }
        };
    }

    public MiningRoleBasedTable generateMiningRoleBasedTable(List<MiningRoleTypeChunk> roles,
            List<MiningUserTypeChunk> users, boolean sortable, double frequency, DetectedPattern intersection,
            double maxFrequency, RoleAnalysisDetectionModeType searchMode) {
        return new MiningRoleBasedTable(ID_DATATABLE, roles, users, sortable, frequency, intersection, maxFrequency, searchMode) {
            @Override
            public void resetTable(AjaxRequestTarget target) {
                updateMiningTable(target, false, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
            }

            @Override
            protected String getCompressStatus() {
                return compressMode;
            }

            @Override
            protected void onPerform(AjaxRequestTarget ajaxRequestTarget) {
                if (compress) {
                    compress = false;
                    compressMode = "EXPAND MODE";
                } else {
                    compress = true;
                    compressMode = "COMPRESS MODE";
                }
                loadMiningTableData(sortMode);

                updateMiningTable(ajaxRequestTarget, false, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
                ajaxRequestTarget.add(this);
            }
        };
    }

    public Component generateTableIntersection(String id, List<DetectedPattern> miningSets) {

        MiningIntersectionTable components = new MiningIntersectionTable(id, miningSets, processMode, clusterOid) {
            @Override
            protected void onLoad(AjaxRequestTarget ajaxRequestTarget, IModel<DetectedPattern> rowModel) {
                intersection = rowModel.getObject();

                updateMiningTable(ajaxRequestTarget, true, searchMode, miningRoleTypeChunks, miningUserTypeChunks);
                processButton.setVisible(true);
                ajaxRequestTarget.add(processButton);
            }
        };

        components.setOutputMarkupId(true);
        return components;
    }

    protected Component getIntersectionTable() {
        return get(((PageBase) getPage()).createComponentPath(ID_DATATABLE_INTERSECTIONS));
    }

    protected MiningRoleBasedTable getMiningRoleBasedTable() {
        return (MiningRoleBasedTable) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE));
    }

    protected MiningUserBasedTable getMiningUserBasedTable() {
        return (MiningUserBasedTable) get(((PageBase) getPage()).createComponentPath(ID_DATATABLE));
    }

    public PageBase getPageBase() {
        return ((PageBase) getPage());
    }

}
