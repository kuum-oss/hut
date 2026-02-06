package org.example.model;

public enum CropMode {
    SKIP,           // Пропустить (не менять)
    FIT_WIDTH,      // Растянуть по ширине
    FIT_HEIGHT,     // Растянуть по высоте
    STRETCH,        // Растянуть на весь экран
    CENTER_ONLY,    // Центрировать
    SMART           // <--- ДОБАВЛЕНО: Умная обрезка полей
}