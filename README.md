# surfgobbypass

**Лёгкий Android-инструмент от zhxstudio: Home → возврат в Surf GO одной кнопкой.**

![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Java 17](https://img.shields.io/badge/Java-17-1565C0)
![Release 2.3](https://img.shields.io/badge/release-2.3-1565C0)

## Возможности

- Обычный белый интерфейс с синими кнопками.
- Перетаскиваемая плавающая кнопка **B**, размер 48–128 dp.
- Доступ через **Root** или **Shizuku**; подготовленные shell-сессии переиспользуются.
- Русский, English и Српски.
- Диагностика с копированием полного текста последней ошибки.
- Подписанная release-сборка, отладка отключена.

Кнопка отправляет Home и запрос возврата в игру. Нет команды force-stop, принудительного завершения процесса или искусственной задержки. Скорость зависит от Android и устройства. Успешный ответ означает принятие команды Android, а не подтверждённое исчезновение рекламы.

## Использование

1. Установите release APK из раздела **Releases**, если владелец репозитория его опубликовал.
2. Выберите язык и Root или Shizuku. Для Shizuku сначала запустите его службу и предоставьте приложению доступ; для Root подтвердите запрос su.
3. Разрешите показ поверх приложений и уведомления.
4. Нажмите «Запустить оверлей», откройте игру и нажмите **B**.
5. При ошибке откройте «Диагностика» → «Копировать» и приложите текст к issue.

## Сборка на Windows — только через gradlew.bat

Android Studio не требуется. Установите **JDK 17**, Android SDK **Platform 35** и **Build Tools 34.0.0**. Укажите `JAVA_HOME` и `ANDROID_HOME` либо создайте локальный `local.properties`:

```properties
sdk.dir=C:/Android/Sdk
```

В PowerShell из папки проекта:

```powershell
.\gradlew.bat
```

Без аргументов wrapper собирает подписанный release APK. Явный вариант и проверка:

```powershell
.\gradlew.bat --no-daemon assembleRelease
.\gradlew.bat --no-daemon lintRelease
```

Результат: `app/build/outputs/apk/release/app-release.apk`.

Первый запуск требует интернет для Gradle и зависимостей. Wrapper автоматически вызывает внутренний `init-signing.ps1` для создания постоянного ключа. Отдельно запускать скрипты не нужно; `start.bat` не используется.

### Подпись и обновления

Сохраните резервную копию **всей папки signing/**. Ключ и пароли исключены из Git и публичного архива. Не загружайте их на GitHub. Для обновления ранее установленного APK нужен тот же ключ. Сборка из чистого архива создаёт новый ключ; для совместимого обновления восстановите свою папку signing перед сборкой.

## Структура

```text
app/src/main/
  java/com/zhx/studiotool/  # Activity, overlay, Root/Shizuku, диагностика
  aidl/                   # интерфейс Shizuku UserService
  res/                    # интерфейс, языки, иконки
  AndroidManifest.xml
gradle/wrapper/            # Gradle Wrapper
tests/                    # проверки обработки shell-ответов
gradlew.bat               # единственная точка входа сборки Windows
init-signing.ps1           # внутренний помощник подписи
```

Целевой пакет меняется в `ShellRunner.java`: `TARGET_PACKAGE = "com.KotGames.SurfGO"`. Стартовая Activity определяется перед отправкой Home и кешируется. Для возврата используется явный компонент Activity.


Проект не является официальным приложением Surf GO. **zhxstudio**.
