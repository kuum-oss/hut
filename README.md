# 📚 Manga & Book Cleaner v17

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-orange?style=for-the-badge&logo=openjdk" alt="Java">
  <img src="https://img.shields.io/badge/UI-Swing-blue?style=for-the-badge" alt="Swing">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
</p>

---

## 📖 Описание
**Manga Cleaner** — это интеллектуальный инструмент для подготовки манги и книг к комфортному чтению на электронных ридерах (Kindle, PocketBook и др.). Программа не просто меняет размер, она **чистит** контент от мусора.

---

## ⚡ Ключевые фишки

### 🧠 Smart Pattern Eraser (PDF)
Самая мощная функция проекта. Если в вашей манге через каждую страницу вставлена реклама (например, от *oceanofpdf*):
1. Программа анализирует первые страницы.
2. При обнаружении цикличной рекламы (паттерна) включается **Turbo Mode**.
3. Рекламные страницы вырезаются автоматически на уровне алгоритма без задержек на чтение текста.

### 🛠 Обработка изображений
* **HD Upscaling:** Увеличение разрешения в 1.5x с использованием бикубической интерполяции для четкости линий.
* **Smart Crop:** Автоматическое удаление пустых полей (кроп) для максимизации области чтения.
* **Binarization:** Оптимизация под E-Ink дисплеи (высокий контраст).

### 📑 Поддержка EPUB
* Мгновенная очистка текстовых водяных знаков внутри структуры книги.
* Сохранение оригинальных метаданных и обложек.

---

## 🚀 Быстрый старт

### Требования
* **Java 17+**
* **Maven** (для управления зависимостями)

### Установка
```bash
git clone [https://github.com/ваш-аккаунт/manga-cleaner.git](https://github.com/ваш-аккаунт/manga-cleaner.git)
cd manga-cleaner
mvn clean install
```

## 📚 Примеры книг
В папке `BookExample(NO_USE)` находятся примеры файлов для демонстрации работы программы. 
> **Внимание:** Эти файлы предоставлены исключительно для ознакомления с функционалом. Пожалуйста, ознакомьтесь с [warning.md](BookExample(NO_USE)/warnin.md).

---

## 🖼️ Визуализация

### 🔄 До и После (Очистка водяных знаков)
| Оригинал (с рекламой/водяными знаками) | После обработки (чистая страница) |
| :---: | :---: |
| ![Before](Photo%20/A.png) | ![After](Photo%20/B.png) |

---

## 📊 Схема работы
Ниже представлена диаграмма процесса обработки документа в приложении:

![Diagram](Photo%20/C.png)

### PlantUML (Код диаграммы)
<details>
<summary>Показать код PlantUML</summary>

```plantuml
@startuml
skinparam backgroundColor #EEEBDC
skinparam handwritten false

actor User
participant "MangaCleanerApp (UI)" as UI
participant "MangaImageProcessor" as Processor
participant "PdfWatermarkCleaner" as PDF
participant "EpubWatermarkCleaner" as EPUB

User -> UI : Drag & Drop File
UI -> UI : Detect File Format

alt PDF Format
    UI -> PDF : Process PDF
    PDF -> PDF : Scan for Pattern Ads
    PDF -> Processor : Process Images (Filters)
    Processor -> Processor : HD Upscale / Binarization / Crop
    PDF -> UI : Save Clean PDF
else EPUB Format
    UI -> EPUB : Process EPUB
    EPUB -> EPUB : Extract XML/HTML
    EPUB -> EPUB : Remove Watermark Patterns
    EPUB -> UI : Save Clean EPUB
end

UI -> User : Show Success Message
@enduml
```
</details>

---

## 🛠️ Стек технологий
* **Java 17**
* **FlatLaf** (Modern UI)
* **iText / PDFBox** (PDF processing)
* **ImageIO** (Image filters)
