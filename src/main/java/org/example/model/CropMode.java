package org.example.model;

public enum CropMode {
    SKIP,           // Пропустить
    FIT_WIDTH,      // По ширине
    FIT_HEIGHT,     // По высоте
    STRETCH,        // Растянуть
    CENTER_ONLY,    // Центрировать
    SMART           // <--- ВАЖНО: Добавьте этот режим!
}