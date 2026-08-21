package com.lbs.server.service

import com.lbs.api.json.model.*
import com.lbs.bot.Bot
import com.lbs.bot.model.TelegramMessageSourceSystem
import com.lbs.server.lang.{En, Localization}
import com.lbs.server.repository.model.Monitoring
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.{any, anyLong, anyString}
import org.mockito.Mockito.{mock, times, verify, when}

import java.time.{LocalDateTime, LocalTime, ZonedDateTime}

class MonitoringServiceSpec {

  private def termAt(dateTime: LocalDateTime, scheduleId: Long): TermExt = {
    val date   = LuxmedFunnyDateTime(dateTimeLocal = Some(dateTime))
    val doctor = Doctor(Some("dr"), None, Some("John"), None, None, 50L, Some("Smith"))
    val term = Term(
      Some("Clinic"),
      10L,
      5L,
      date,
      date,
      doctor,
      None,
      isAdditional = false,
      isImpediment = false,
      isTelemedicine = false,
      1L,
      scheduleId,
      100L
    )
    TermExt(AdditionalData(isPreparationRequired = false, preparationItems = Nil), term)
  }

  private def monitoring: Monitoring = {
    val monitoring = Monitoring(
      userId = 1L,
      username = "testuser",
      accountId = 1L,
      chatId = "1",
      sourceSystemId = TelegramMessageSourceSystem.id,
      payerId = 1L,
      cityId = 1L,
      cityName = "Krakow",
      clinicId = Some(10L),
      clinicName = "Clinic",
      serviceId = 100L,
      serviceName = "Consultation",
      doctorId = None,
      doctorName = "Any",
      dateFrom = ZonedDateTime.now(),
      dateTo = ZonedDateTime.now().plusMonths(1),
      timeFrom = LocalTime.of(7, 0),
      timeTo = LocalTime.of(21, 0),
      offset = 0
    )
    monitoring.recordId = 1L
    monitoring
  }

  @Test
  def notifyOnlyWhenAnEarlierTermAppearsAndKeepMonitoringActive(): Unit = {
    val bot          = mock(classOf[Bot])
    val dataService  = mock(classOf[DataService])
    val localization = mock(classOf[Localization])
    val service      = new MonitoringService
    service.bot = bot
    service.dataService = dataService
    service.localization = localization
    when(localization.lang(anyLong())).thenReturn(En)
    when(dataService.saveMonitoring(any())).thenAnswer(invocation => invocation.getArgument(0))

    val activeMonitoring = monitoring
    val firstBest        = LocalDateTime.of(2026, 9, 10, 10, 0)
    val later            = LocalDateTime.of(2026, 9, 10, 12, 0)
    val better           = LocalDateTime.of(2026, 9, 10, 9, 0)

    service.handleAvailableTerms(Seq(termAt(later, 2L), termAt(firstBest, 1L)), activeMonitoring)

    assertThat(activeMonitoring.bestTermDateTime).isEqualTo(firstBest)
    assertThat(activeMonitoring.active).isTrue
    verify(bot, times(1)).sendMessage(any(), anyString())
    verify(dataService, times(1)).saveMonitoring(activeMonitoring)

    service.handleAvailableTerms(Seq(termAt(firstBest, 1L), termAt(later, 2L)), activeMonitoring)

    verify(bot, times(1)).sendMessage(any(), anyString())
    verify(dataService, times(1)).saveMonitoring(activeMonitoring)

    service.handleAvailableTerms(Seq(termAt(firstBest, 1L), termAt(better, 3L)), activeMonitoring)

    assertThat(activeMonitoring.bestTermDateTime).isEqualTo(better)
    assertThat(activeMonitoring.active).isTrue
    verify(bot, times(2)).sendMessage(any(), anyString())
    verify(dataService, times(2)).saveMonitoring(activeMonitoring)
  }
}
