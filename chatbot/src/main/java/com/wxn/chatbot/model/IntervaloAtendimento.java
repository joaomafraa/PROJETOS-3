package com.wxn.chatbot.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class IntervaloAtendimento {

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, columnDefinition = "varchar(10)")
    private DayOfWeek diaSemana;

    @Column(nullable = false)
    private LocalTime abertura;

    @Column(nullable = false)
    private LocalTime fechamento;

    protected IntervaloAtendimento() {
    }

    public IntervaloAtendimento(DayOfWeek diaSemana, LocalTime abertura, LocalTime fechamento) {
        this.diaSemana = Objects.requireNonNull(diaSemana, "Informe o dia da semana.");
        this.abertura = Objects.requireNonNull(abertura, "Informe o horário de abertura.");
        this.fechamento = Objects.requireNonNull(fechamento, "Informe o horário de fechamento.");
        if (!abertura.isBefore(fechamento)) {
            throw new IllegalArgumentException("O horário de abertura deve ser antes do fechamento.");
        }
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getAbertura() {
        return abertura;
    }

    public LocalTime getFechamento() {
        return fechamento;
    }

    public boolean contem(DayOfWeek dia, LocalTime hora) {
        return diaSemana == dia && !hora.isBefore(abertura) && hora.isBefore(fechamento);
    }

    boolean sobrepoe(IntervaloAtendimento outro) {
        return diaSemana == outro.diaSemana
                && abertura.isBefore(outro.fechamento)
                && outro.abertura.isBefore(fechamento);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntervaloAtendimento outro)) return false;
        return diaSemana == outro.diaSemana && abertura.equals(outro.abertura) && fechamento.equals(outro.fechamento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(diaSemana, abertura, fechamento);
    }
}
