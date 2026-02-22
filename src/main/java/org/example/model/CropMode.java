package org.example.model;

public enum CropMode {
    SKIP,           // Пропустить (не менять)
    FIT_WIDTH,      // Растянуть по ширине
    FIT_HEIGHT,     // Растянуть по высоте
    STRETCH,        // Растянуть на весь экран
    CENTER_ONLY,    // Центрировать
    SMART,          // Умная обрезка полей
    MANUAL_4_CRIT   // Обычная обрезка по 4 критериям
}