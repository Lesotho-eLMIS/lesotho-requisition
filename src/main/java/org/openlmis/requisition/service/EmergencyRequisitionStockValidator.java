/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.requisition.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.openlmis.requisition.domain.requisition.Requisition;
import org.openlmis.requisition.domain.requisition.RequisitionLineItem;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.OrderableDto;
import org.openlmis.requisition.dto.VersionIdentityDto;
import org.openlmis.requisition.dto.stockmanagement.StockCardSummaryDto;
import org.openlmis.requisition.service.referencedata.FacilityReferenceDataService;
import org.openlmis.requisition.service.stockmanagement.StockCardSummariesStockManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmergencyRequisitionStockValidator {

    private static final String SERVICE_POINT_TYPE_CODE = "service_point";
    private static final String DON_PREFIX_PATTERN = "DON\\d+-";

    @Autowired
    private FacilityReferenceDataService facilityReferenceDataService;

    @Autowired
    private StockCardSummariesStockManagementService stockCardSummariesStockManagementService;

    /**
     * Holds the result of stock validation containing both hard errors
     * (out of stock products) and soft warnings (insufficient stock products).
     */
    public static class StockValidationResult {

        private final List<String> outOfStockProducts;
        private final Map<String, Integer> insufficientStockProducts;

        public StockValidationResult(List<String> outOfStockProducts,
                                     Map<String, Integer> insufficientStockProducts) {
            this.outOfStockProducts = outOfStockProducts;
            this.insufficientStockProducts = insufficientStockProducts;
        }

        // No errors and no warnings — validation fully passed
        public boolean isFullyValid() {
            return outOfStockProducts.isEmpty() && insufficientStockProducts.isEmpty();
        }

        // Hard block — at least one product has zero stock and no DON fallback
        public boolean hasErrors() {
            return !outOfStockProducts.isEmpty();
        }

        // Soft warning — at least one product has stock but less than requested
        public boolean hasWarnings() {
            return !insufficientStockProducts.isEmpty();
        }

        public List<String> getOutOfStockProducts() {
            return outOfStockProducts;
        }

        public Map<String, Integer> getInsufficientStockProducts() {
            return insufficientStockProducts;
        }
    }

    /**
     * Validates stock availability for emergency requisitions from service points.
     * Uses the supplying facility already assigned to the requisition.
     * Checks DON equivalents when original product has no stock.
     * Returns both hard errors (zero stock) and soft warnings (insufficient stock).
     *
     * @param requisition the emergency requisition being submitted
     * @param orderables  map of orderable DTOs loaded from reference data
     * @return StockValidationResult containing errors and warnings
     */
    public StockValidationResult validate(Requisition requisition,
                                          Map<VersionIdentityDto, OrderableDto> orderables) {

        List<String> outOfStockProducts = new ArrayList<>();
        Map<String, Integer> insufficientStockProducts = new HashMap<>();

        // Only run for emergency requisitions
        if (!Boolean.TRUE.equals(requisition.getEmergency())) {
            return new StockValidationResult(outOfStockProducts, insufficientStockProducts);
        }

        // Fetch facility and check if it's a service point
        FacilityDto facility = facilityReferenceDataService
                .findOne(requisition.getFacilityId());

        if (facility == null
                || facility.getType() == null
                || !SERVICE_POINT_TYPE_CODE.equals(facility.getType().getCode())) {
            return new StockValidationResult(outOfStockProducts, insufficientStockProducts);
        }

        // Get the supplying facility directly from the requisition
        // No need to fetch supply lines — the requisition already knows its supplier
        UUID supplyingFacilityId = requisition.getSupplyingFacilityId();

        if (supplyingFacilityId == null) {
            return new StockValidationResult(outOfStockProducts, insufficientStockProducts);
        }

        // Collect orderable IDs from line items
        Set<UUID> orderableIds = requisition.getRequisitionLineItems()
                .stream()
                .map(lineItem -> lineItem.getOrderable().getId())
                .collect(Collectors.toSet());

        // Fetch SOH at the supplying facility
        List<StockCardSummaryDto> stockSummaries = stockCardSummariesStockManagementService
                .search(requisition.getProgramId(), supplyingFacilityId,
                        orderableIds, LocalDate.now());

        // Build orderableId -> SOH map
        Map<UUID, Integer> stockByOrderableId = new HashMap<>();
        for (StockCardSummaryDto summary : stockSummaries) {
            if (summary.getOrderable() == null) {
                continue;
            }
            UUID orderableId = summary.getOrderable().getId();
            Integer soh = summary.getStockOnHand() != null ? summary.getStockOnHand() : 0;
            stockByOrderableId.put(orderableId, soh);
        }

        // Build productCode -> SOH map for DON prefix lookup
        Map<String, Integer> stockByProductCode = orderables.values()
                .stream()
                .filter(o -> o.getProductCode() != null
                        && stockByOrderableId.containsKey(o.getId()))
                .collect(Collectors.toMap(
                        OrderableDto::getProductCode,
                        o -> stockByOrderableId.getOrDefault(o.getId(), 0),
                        (existing, replacement) -> existing
                ));

        // Precompute DON mappings — strip DON prefix to get original code
        // e.g. DON-ABA001-TAB001-30 -> ABA001-TAB001-30
        Map<String, Integer> donStockByOriginalCode = new HashMap<>();
        for (Map.Entry<String, Integer> entry : stockByProductCode.entrySet()) {
            String code = entry.getKey();
            if (code.matches(DON_PREFIX_PATTERN + ".*")) {
                // Strip the DON prefix to get the original product code
                String originalCode = code.replaceFirst(DON_PREFIX_PATTERN, "");
                // Aggregate DON stock for the same original product
                donStockByOriginalCode.merge(originalCode, entry.getValue(), Integer::sum);
            }
        }

        // Check each line item
        for (RequisitionLineItem lineItem : requisition.getRequisitionLineItems()) {

            UUID orderableId = lineItem.getOrderable().getId();

            VersionIdentityDto identity = new VersionIdentityDto(
                    lineItem.getOrderable().getId(),
                    lineItem.getOrderable().getVersionNumber()
            );
            OrderableDto orderable = orderables.get(identity);

            if (orderable == null || orderable.getProductCode() == null) {
                continue;
            }

            String productCode = orderable.getProductCode();
            Integer requestedQuantity = lineItem.getRequestedQuantity();

            // Get SOH for this product at the supplying facility
            int originalSoh = stockByOrderableId.getOrDefault(orderableId, 0);

            // Get DON equivalent SOH using precomputed map
            int donSoh = donStockByOriginalCode.getOrDefault(productCode, 0);

            // Total available stock = original + DON equivalent
            int totalAvailableStock = originalSoh + donSoh;

            if (totalAvailableStock == 0) {
                // Hard error — no stock, not even DON equivalent
                outOfStockProducts.add(productCode);
            } else if (requestedQuantity != null && totalAvailableStock < requestedQuantity) {
                // Soft warning — some stock but not enough to fulfill full request
                insufficientStockProducts.put(productCode, totalAvailableStock);
            }
        }

        return new StockValidationResult(outOfStockProducts, insufficientStockProducts);
    }
}