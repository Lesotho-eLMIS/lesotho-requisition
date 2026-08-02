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
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.requisition.web;

import static org.openlmis.requisition.domain.requisition.RequisitionStatus.APPROVED;
import static org.openlmis.requisition.domain.requisition.RequisitionStatus.RELEASED_WITHOUT_ORDER;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.openlmis.requisition.domain.requisition.Requisition;
import org.openlmis.requisition.domain.requisition.RequisitionLineItem;
import org.openlmis.requisition.domain.requisition.StatusChange;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.NdsoRequisitionLineItemDto;
import org.openlmis.requisition.dto.NdsoRequisitionReportDto;
import org.openlmis.requisition.dto.OrderableDto;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.UserDto;
import org.openlmis.requisition.dto.VersionIdentityDto;
import org.openlmis.requisition.i18n.MessageKeys;
import org.openlmis.requisition.i18n.MessageService;
import org.openlmis.requisition.service.PeriodService;
import org.openlmis.requisition.service.referencedata.FacilityReferenceDataService;
import org.openlmis.requisition.service.referencedata.OrderableReferenceDataService;
import org.openlmis.requisition.service.referencedata.ProgramReferenceDataService;
import org.openlmis.requisition.service.referencedata.UserReferenceDataService;
import org.openlmis.requisition.utils.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NdsoRequisitionReportDtoBuilder {

  private static final String NULL_VALUE = "NULL";
  private static final DateTimeFormatter GENERATED_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Autowired
  private FacilityReferenceDataService facilityReferenceDataService;

  @Autowired
  private ProgramReferenceDataService programReferenceDataService;

  @Autowired
  private OrderableReferenceDataService orderableReferenceDataService;

  @Autowired
  private UserReferenceDataService userReferenceDataService;

  @Autowired
  private PeriodService periodService;

  @Autowired
  private MessageService messageService;

  @Autowired
  private Clock clock;

  /**
   * Builds the data used by the NDSO requisition print.
   *
   * @param requisition approved requisition to print
   * @return report data
   */
  public NdsoRequisitionReportDto build(Requisition requisition) {
    FacilityDto facility = facilityReferenceDataService.findOne(requisition.getFacilityId());
    ProgramDto program = programReferenceDataService.findOne(requisition.getProgramId());
    ProcessingPeriodDto period = periodService.getPeriod(requisition.getProcessingPeriodId());

    Map<VersionIdentityDto, OrderableDto> orderables = orderableReferenceDataService
        .findByIdentities(requisition.getAllOrderables())
        .stream()
        .collect(Collectors.toMap(OrderableDto::getIdentity, Function.identity()));

    List<NdsoRequisitionLineItemDto> lineItems = requisition.getRequisitionLineItems()
        .stream()
        .filter(line -> !Boolean.TRUE.equals(line.getSkipped()))
        .map(line -> toLineItemDto(line, orderables))
        .sorted(Comparator.comparing(NdsoRequisitionLineItemDto::getProductName,
            String.CASE_INSENSITIVE_ORDER))
        .collect(Collectors.toList());

    String districtName = Optional.ofNullable(facility)
        .map(FacilityDto::getGeographicZone)
        .map(zone -> zone.getParent())
        .map(zone -> zone.getName())
        .orElse(NULL_VALUE);

    return new NdsoRequisitionReportDto(
        "Facility - Monthly NDSO - " + valueOrNull(program.getName()) + " - eLMIS",
        formatPeriod(period),
        districtName,
        valueOrNull(facility.getName()),
        findApproverName(requisition),
        GENERATED_DATE_FORMAT.format(LocalDate.now(clock)),
        lineItems
    );
  }

  private NdsoRequisitionLineItemDto toLineItemDto(RequisitionLineItem line,
      Map<VersionIdentityDto, OrderableDto> orderables) {
    OrderableDto orderable = orderables.get(new VersionIdentityDto(line.getOrderable()));

    return new NdsoRequisitionLineItemDto(
        valueOrNull(null == orderable ? null : orderable.getProductCode()),
        valueOrNull(null == orderable ? null : orderable.getFullProductName()),
        valueOrNull(line.getAverageConsumption()),
        valueOrNull(line.getStockOnHand()),
        valueOrNull(line.getPacksToShip())
    );
  }

  private String formatPeriod(ProcessingPeriodDto period) {
    LocalDate startDate = null == period ? null : period.getStartDate();
    if (null == startDate) {
      return NULL_VALUE;
    }

    return startDate.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        + " " + startDate.getYear();
  }

  private String findApproverName(Requisition requisition) {
    if (null == requisition.getStatusChanges()) {
      return NULL_VALUE;
    }

    Optional<StatusChange> approval = requisition.getStatusChanges()
        .stream()
        .filter(change -> APPROVED == change.getStatus()
            || Boolean.TRUE.equals(requisition.getReportOnly())
            && RELEASED_WITHOUT_ORDER == change.getStatus())
        .max(Comparator.comparing(StatusChange::getCreatedDate,
            Comparator.nullsFirst(Comparator.naturalOrder())));

    if (!approval.isPresent()) {
      return NULL_VALUE;
    }

    if (null == approval.get().getAuthorId()) {
      return messageService.localize(
          new Message(MessageKeys.STATUS_CHANGE_USER_SYSTEM)).asMessage();
    }

    UserDto user = userReferenceDataService.findOne(approval.get().getAuthorId());
    return null == user ? NULL_VALUE : valueOrNull(user.printName());
  }

  private String valueOrNull(Object value) {
    return null == value ? NULL_VALUE : value.toString();
  }
}
