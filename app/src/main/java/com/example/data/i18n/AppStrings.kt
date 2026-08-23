package com.example.data.i18n

import androidx.compose.ui.unit.LayoutDirection

interface AppStrings {
    val layoutDirection: LayoutDirection
    val appName: String
    val appSubtitle: String
    val homeTab: String
    val queueTab: String
    val historyTab: String
    val settingsTab: String

    // Categories
    val videoSection: String
    val videoDesc: String
    val audioSection: String
    val audioDesc: String
    val imageSection: String
    val imageDesc: String
    val documentSection: String
    val documentDesc: String
    val convertSection: String
    val convertDesc: String
    val conversionModeLabel: String
    val conversionModePrintTitle: String
    val conversionModePrintDesc: String
    val conversionModeScreenshotTitle: String
    val conversionModeScreenshotDesc: String

    // Common Controls
    val selectFile: String
    val selectFiles: String
    val startProcessing: String
    val cancel: String
    val confirm: String
    val pause: String
    val resume: String
    val delete: String
    val clearAll: String
    val save: String
    val saveNamePrompt: String
    val savePathNotice: String
    val saveSuffixPrompt: String
    val saveMultiFileExample: String
    val saveMultiFileNotice: String
    val saveNameInvalidError: String
    val saveNameSanitizedNotice: String
    val unsupportedFileTypeTitle: String
    val unsupportedFileTypeMessage: String
    val openFile: String
    val shareFile: String
    val copyErrorLog: String
    val ok: String
    val back: String

    // Processor Badge
    val hwProcessorName: String
    val hwProcessorDesc: String
    val swProcessorName: String
    val swProcessorDesc: String
    val processorDialogTitle: String
    val processorDialogBody: String
    val videoFullReencodingNotice: String
    val videoMethodRealEncoding: String
    val videoMethodPassthrough: String

    // Compression Presets
    val presetLightTitle: String
    val presetLightDesc: String
    val presetMediumTitle: String
    val presetMediumDesc: String
    val presetHeavyTitle: String
    val presetHeavyDesc: String
    val presetCustomTitle: String
    val presetCustomDesc: String
    val customControlsTitle: String
    val customControlsHint: String
    val customQualityLabel: String
    val customAudioQualityLabel: String
    val customMaxDimensionLabel: String
    val customBitrateLabel: String
    val customNamingPatternLabel: String
    val customNamingPatternHint: String
    val customNamingPatternSave: String
    val compressionPresetLabel: String

    // Video Options
    val muteAudioLabel: String
    val muteAudioDesc: String
    val videoStepTrimRotate: String
    val rotateLabel: String
    val trimLabel: String
    val trimStart: String
    val trimEnd: String

    // Audio Options
    val extractFromVideoLabel: String
    val extractFromVideoDesc: String

    // Image Options
    val combineToPdfLabel: String
    val removeExifLabel: String

    // Document Options
    val pdfOperationCompress: String
    val pdfOperationSplit: String
    val pdfOperationMerge: String
    val pdfOperationExtractText: String
    val splitAllPages: String
    val splitSpecificRange: String
    val rangePlaceholder: String

    // Conversion Options
    val convertNoticeTitle: String
    val convertNoticeBody: String

    // Dynamic Island & Status
    val statusIdle: String
    val statusProcessing: String
    val statusSuccess: String
    val statusError: String
    val videoCompressionSkippedNotice: String
    val videoCompressionSkippedShort: String
    val compressionOutcomeSuccess: String
    val compressionOutcomeMarginal: String
    val compressionOutcomeNone: String

    // Queue Screen
    val queueEmptyTitle: String
    val queueEmptySubtitle: String
    val activeTaskHeader: String
    val pendingTasksHeader: String

    // History Screen
    val historyEmptyTitle: String
    val historyEmptySubtitle: String
    val historyClearConfirmTitle: String
    val historyClearConfirmBody: String

    // Settings Screen
    val languageSection: String
    val languageArabic: String
    val languageEnglish: String
    val themeSection: String
    val darkTheme: String
    val lightTheme: String
    val saveFolderSection: String
    val currentFolderLabel: String
    val changeFolderButton: String
    val batteryOptimizationTitle: String
    val batteryOptimizationDesc: String
    val fixBatteryButton: String
    val cleanTempFilesTitle: String
    val cleanTempFilesDesc: String
    val cleanTempButton: String
    val tempFilesCleanedToast: String

    // Dialogs & Confirmations
    val confirmStartTitle: String
    val confirmStartBody: String
    val confirmCancelTitle: String
    val confirmCancelBody: String
    val confirmLanguageChangeTitle: String
    val confirmLanguageChangeBody: String
    val confirmClearSelectionTitle: String
    val confirmClearSelectionBody: String
    // Missing properties for screens
    val arabic: String
    val english: String
    val clearHistory: String
    val clearHistoryConfirm: String
    val emptyHistory: String
    val emptyQueue: String
    val statusPaused: String
    val convertNotice: String
    val languageSetting: String
    val themeSetting: String
    val storagePathSetting: String
    val changePath: String
    val batteryOptTitle: String
    val batteryOptDesc: String
    val batteryOptButton: String
    val cleanTempSuccess: String
    val cleanTempFiles: String
    val offlineDisclaimer: String

    val notificationPauseResume: String
    val notificationCancel: String
    val renameFile: String
    val newFileNamePrompt: String
    val fileRenamedSuccess: String
    val fileRenameError: String
    val notificationPermissionTitle: String
    val notificationPermissionBody: String
    val notificationPermissionDeniedWarning: String
    val storagePermissionTitle: String
    val storagePermissionBody: String
    val openAppSettings: String

    val generalSettings: String
    val storageAndNaming: String
    val cumulativeSpaceSaved: String
    val autoNamingPattern: String
    val notificationsAndPermissions: String
    val processingNotifications: String
    val openAppSettingsDesc: String
    val advancedAndMaintenance: String
    val showTechnicalBadges: String
    val showTechnicalBadgesDesc: String

    val searchInHistory: String
    val noSearchResults: String
    val changeFile: String
    val renameFailed: String

    // Error Messages
    val errorVideoTranscodeFailed: String
    /** Shown when compression could not shrink the file but a playable copy was still saved. */
    val videoSavedWithoutCompressionNotice: String
    val errorProcessingTimeout: String
    val errorAudioExtractFailed: String
    val errorAudioTranscodeFailed: String
    val errorPdfTextExtractionFailed: String
    val errorPdfPasswordProtected: String
    val errorInvalidPageRange: String
    val warnSingleFileOperation: String
    val errorLegacyOfficeFormat: String
    val errorUnsupportedOfficeFormat: String
    val errorStoragePermissionDenied: String
    val errorSaveFinalOutputFailed: String
    val officeConvertDisclaimer: String
    fun errorInsufficientCacheStorage(required: String): String
    fun errorInsufficientStorage(required: String): String
    fun errorDocxEmptyContent(fileName: String): String
    fun errorXlsxEmptyContent(fileName: String): String
    fun errorPptxEmptyContent(fileName: String): String
    fun errorOfficeConversionFailed(fileName: String, details: String): String
    fun errorSaveToDownloadsFailed(details: String): String

    // Dynamic Island idle-state cycling greetings (multi-language, shown regardless of app language)
    val idleGreetings: List<String>

    val errorImageToPdfAllFailed: String
    val errorImageProcessAllFailed: String

    // Hardware vs Software processing info dialog (HistoryScreen)
    val hwSwInfoTitle: String
    val hwSwInfoHardwareBody: String
    val hwSwInfoSoftwareBody: String

    // History filter chips
    val filterAll: String
    val filterVideo: String
    val filterAudio: String
    val filterImage: String
    val filterDocument: String
    val shareOpenAppFailed: String

    // Safe-mode compression notice
    val safeModeSwitchedNotice: String

    // Image screen notices
    val imageTransparentPngNotice: String
    val imageExifPrivacyNotice: String

    // Image output format selector
    val imageOutputFormatTitle: String
    val imageFormatAutoTitle: String
    val imageFormatAutoDesc: String
    val imageFormatJpgTitle: String
    val imageFormatJpgDesc: String
    val imageFormatPngTitle: String
    val imageFormatPngDesc: String
    val imageFormatWebpTitle: String
    val imageFormatWebpDesc: String

    // Misc English-only fallback strings (now localized)
    val errorFileDoesNotExist: String
    val errorCannotOpenFile: String
    val errorInvalidVideoFile: String
    val errorInvalidAudioFile: String
    val audioBackgroundToggleLabel: String
    val audioBackgroundToggleDesc: String
    val audioRequireChargingLabel: String
    val audioFlacCompatWarning: String
    val audioStepSelectFiles: String
    val audioStepConfigureQuality: String
    val audioStepAdditionalOptions: String
    val videoHighRiskContainerWarning: String
    val moveUpDescription: String
    val moveDownDescription: String
    val dragHandleDescription: String
    val successDescription: String
    val hwSwInfoDescription: String
}

val ArabicStrings: AppStrings = StringsArabic
val EnglishStrings: AppStrings = StringsEnglish

fun getAppStrings(langCode: String?): AppStrings {
    return if (langCode?.equals("en", ignoreCase = true) == true) StringsEnglish else StringsArabic
}

object StringsArabic : AppStrings {
    override val layoutDirection: LayoutDirection = LayoutDirection.Rtl
    override val appName: String = "Vada"
    override val appSubtitle: String = "معالجة وتحويل وسائط محلي بالكامل"
    override val homeTab: String = "الرئيسية"
    override val queueTab: String = "الطابور"
    override val historyTab: String = "السجل"
    override val settingsTab: String = "الإعدادات"

    override val videoSection: String = "قسم الفيديوهات"
    override val videoDesc: String = "ضغط • قص • تدوير • إزالة الصوت — كله بدون إنترنت"
    override val audioSection: String = "قسم الصوت"
    override val audioDesc: String = "استخراج، دمج وضغط الملفات الصوتية"
    override val imageSection: String = "قسم الصور"
    override val imageDesc: String = "ضغط، تحويل الصور وتجميعها لـ PDF"
    override val documentSection: String = "قسم المستندات"
    override val documentDesc: String = "ضغط، تقسيم، دمج واستخراج نصوص PDF"
    override val convertSection: String = "قسم التحويلات إلى PDF"
    override val convertDesc: String = "تحويل مستندات Word, Excel, PPT إلى PDF"
    override val conversionModeLabel: String = "طريقة التحويل"
    override val conversionModePrintTitle: String = "طباعة (موصى بها)"
    override val conversionModePrintDesc: String = "مثل «طباعة إلى PDF» في النظام. نص أوضح وقابل للنسخ غالباً، حجم متوازن بعد الضغط."
    override val conversionModeScreenshotTitle: String = "لقطة شاشة داخلية"
    override val conversionModeScreenshotDesc: String = "صفحات كصور مضغوطة. غالباً ملف أصغر، لكن النص غير قابل للنسخ والتكبير أقل حدة."

    override val selectFile: String = "اختيار ملف"
    override val selectFiles: String = "اختيار عدة ملفات"
    override val startProcessing: String = "بدء المعالجة"
    override val cancel: String = "إلغاء"
    override val confirm: String = "تأكيد"
    override val pause: String = "إيقاف مؤقت"
    override val resume: String = "استئناف"
    override val delete: String = "حذف"
    override val clearAll: String = "مسح الكل"
    override val save: String = "حفظ"
    override val saveNamePrompt: String = "اسم الملف الناتج:"
    override val savePathNotice: String = "سيتم حفظ الملف إجبارياً في المجلد العام Downloads/Vada"
    override val saveSuffixPrompt: String = "لاحقة التسمية المشتركة لكل ملف:"
    override val saveMultiFileExample: String = "مثال على الناتج:"
    override val saveMultiFileNotice: String = "سيتم إنشاء مهمة منفصلة لكل ملف، وكل ملف يُحفظ بشكل مستقل (بدون ضغط ZIP)."
    override val saveNameInvalidError: String = "اسم غير صالح. الرموز / \\ : * ? \" < > | غير مسموح بها في أسماء الملفات."
    override val saveNameSanitizedNotice: String = "سيتم استخدام الاسم بعد إزالة الرموز الممنوعة:"
    override val unsupportedFileTypeTitle: String = "نوع الملف غير مدعوم"
    override val unsupportedFileTypeMessage: String = "هذا النوع من الملفات غير مدعوم. الأنواع المدعومة: فيديو، صوت، صور، PDF، وملفات Word/Excel/PowerPoint."
    override val openFile: String = "فتح الملف"
    override val shareFile: String = "مشاركة"
    override val copyErrorLog: String = "نسخ سجل الخطأ"
    override val ok: String = "حسناً"
    override val back: String = "رجوع"

    override val hwProcessorName: String = "معالجة سريعة (شريحة مخصصة داخل جهازك)"
    override val hwProcessorDesc: String = "استخدام الهاردوير المباشر بدون إعادة ترميز للسرعة القصوى"
    override val swProcessorName: String = "معالجة متقدمة (معالج هاتفك الرئيسي)"
    override val swProcessorDesc: String = "معالجة برمجية دقيقة للتحكم الكامل بالجودة والتفاصيل"
    override val processorDialogTitle: String = "تفاصيل طريقة المعالجة"
    override val processorDialogBody: String = "التطبيق يعتمد على معالجة محلية 100% بدون أي اتصال بالإنترنت.\n\n• المعالجة السريعة: تستفيد من شرائح الميديا بهاتفك لنقل وتمرير البيانات مباشرة بسرعةائقة.\n• المعالجة المتقدمة: تستخدم خوارزميات برمجية لإعادة ضغط وتحويل النصوص والهياكل بأعلى دقة."
    override val videoFullReencodingNotice: String = "سيتم استخدام إعادة الترميز الكاملة"
    override val videoMethodRealEncoding: String = "إعادة ترميز كاملة عبر الهاردوير"
    override val videoMethodPassthrough: String = "تمرير مباشر للحزم (Remux)"

    override val presetLightTitle: String = "خفيف (Light)"
    override val presetLightDesc: String = "حفظ 20% - 30% مع الاحتفاظ بالجودة الأصلية تماماً"
    override val presetMediumTitle: String = "متوسط (Medium)"
    override val presetMediumDesc: String = "توازن مثالي: توفير 50% من المساحة وبجودة عالية جداً"
    override val presetHeavyTitle: String = "شديد (Heavy)"
    override val presetHeavyDesc: String = "أقصى ضغط: توفير 70% - 80% من حجم الملف"
    override val presetCustomTitle: String = "تحكم مخصص (Custom)"
    override val presetCustomDesc: String = "تحديد المعايير ومعدل البت يدوياً"
    override val customControlsTitle: String = "إعدادات مخصصة"
    override val customControlsHint: String = "اسحب لتحديد الجودة والأبعاد ومعدل البت بنفسك"
    override val customQualityLabel: String = "جودة الصورة"
    override val customAudioQualityLabel: String = "جودة الصوت"
    override val customMaxDimensionLabel: String = "أقصى دقة / أبعاد"
    override val customBitrateLabel: String = "معدل البت للفيديو"
    override val customNamingPatternLabel: String = "نمط مخصص"
    override val customNamingPatternHint: String = "مثال: {name}_مضغوط — استخدم {name} كمتغير"
    override val customNamingPatternSave: String = "حفظ النمط"
    override val compressionPresetLabel: String = "اختر مستوى الضغط المطلوب:"

    override val muteAudioLabel: String = "إزالة الصوت بالكامل من الفيديو"
    override val videoStepTrimRotate: String = "٣. قص وتدوير (اختياري)"
    override val rotateLabel: String = "تدوير الفيديو"
    override val trimLabel: String = "قص جزء من الفيديو"
    override val trimStart: String = "البداية"
    override val trimEnd: String = "النهاية"
    override val muteAudioDesc: String = "إنتاج فيديو صامت مفرغ من المقاطع الصوتية"

    override val extractFromVideoLabel: String = "استخراج الصوت من فيديو"
    override val extractFromVideoDesc: String = "سحب التراك الصوتي المباشر من ملف فيديو"

    override val combineToPdfLabel: String = "تجميع الصور إلى مستند PDF واحد"
    override val removeExifLabel: String = "إزالة بيانات EXIF والبيانات الوصفية"

    override val pdfOperationCompress: String = "ضغط مستندات PDF"
    override val pdfOperationSplit: String = "تقسيم ملف PDF"
    override val pdfOperationMerge: String = "دمج عدة ملفات PDF"
    override val pdfOperationExtractText: String = "استخراج النصوص القابلة للنسخ"
    override val splitAllPages: String = "تقسيم كل صفحة لملف مستقل (ملف ZIP)"
    override val splitSpecificRange: String = "استخراج نطاق/صفحات معينة"
    override val rangePlaceholder: String = "مثال: 1-3, 5, 8"

    override val convertNoticeTitle: String = "طبقة نصوص حقيقية"
    override val convertNoticeBody: String = "التحويل ينتج ملف PDF ذو جودة عالية وبطبقة نصوص محددة قابلة للبحث والنسخ على أي جهاز."

    override val statusIdle: String = "Vada جاهز للعمل"
    override val statusProcessing: String = "جاري المعالجة..."
    override val statusSuccess: String = "تم بنجاح ✅"
    override val statusError: String = "حدث خطأ ❌"
    override val videoCompressionSkippedNotice: String = "تعذر ضغط الفيديو، تم حفظ نسخة بدون تغيير الحجم"
    override val videoCompressionSkippedShort: String = "تم الحفظ بدون ضغط ⚠️"
    override val compressionOutcomeSuccess: String = "ضغط ناجح ✅"
    override val compressionOutcomeMarginal: String = "ضغط طفيف ⚠️"
    override val compressionOutcomeNone: String = "لم يتم الضغط ❌"

    override val queueEmptyTitle: String = "لا توجد عناصر حالياً"
    override val queueEmptySubtitle: String = "قم بإضافة مهمة جديدة من الشاشات الرئيسية لبدء المعالجة"
    override val activeTaskHeader: String = "المهمة القائمة حالياً"
    override val pendingTasksHeader: String = "قائمة الانتظار"

    override val historyEmptyTitle: String = "السجل فارغ"
    override val historyEmptySubtitle: String = "الملفات والمعالجات السابقة ستظهر هنا تلقائياً"
    override val historyClearConfirmTitle: String = "مسح السجل بالكامل"
    override val historyClearConfirmBody: String = "هل أنت تأكد من رغبتك في حذف جميع سجلات المعالجات؟ لن تؤثر هذه العملية على الملفات الأصلية."

    override val languageSection: String = "لغة الواجهة"
    override val languageArabic: String = "العربية (RTL)"
    override val languageEnglish: String = "English (LTR)"
    override val themeSection: String = "المظهر والهوية البصرية"
    override val darkTheme: String = "الوضع الداكن (Deep Luxury Ocean)"
    override val lightTheme: String = "الوضع الفاتح"
    override val saveFolderSection: String = "مسار الحفظ الإجباري"
    override val currentFolderLabel: String = "المسار الحالي:"
    override val changeFolderButton: String = "اختيار مجلد مخصص (SAF)"
    override val batteryOptimizationTitle: String = "تحسين استهلاك البطارية"
    override val batteryOptimizationDesc: String = "لضمان استمرار المعالجات الكبيرة في الخلفية بدون توقف، يرجى استثناء التطبيق من قيود توفير الطاقة."
    override val fixBatteryButton: String = "استثناء من توفير البطارية"
    override val cleanTempFilesTitle: String = "الملفات المؤقتة"
    override val cleanTempFilesDesc: String = "تنظيف كل الملفات المؤقتة التي تم إنشاؤها أثناء المعالجة"
    override val cleanTempButton: String = "تنظيف الملفات المؤقتة الآن"
    override val tempFilesCleanedToast: String = "تم مسح جميع الملفات المؤقتة بنجاح"

    override val confirmStartTitle: String = "تأكيد بدء المهمة"
    override val confirmStartBody: String = "هل تريد بدء معالجة الملفات المحددة بالخيارات الحالية؟"
    override val confirmCancelTitle: String = "إلغاء المهمة"
    override val confirmCancelBody: String = "هل أنت متأكد من رغبتك في إلغاء هذه المعالجة؟ سيتم حذف جميع البيانات المؤقتة."
    override val confirmLanguageChangeTitle: String = "تغيير لغة التطبيق"
    override val confirmLanguageChangeBody: String = "سيتم تحديث واجهة التطبيق فوراً. هل تريد المتابعة؟"
    override val confirmClearSelectionTitle: String = "مسح الملفات المختارة"
    override val confirmClearSelectionBody: String = "سيتم مسح الملف المختار حالياً، هل تريد المتابعة؟"

    override val arabic: String = "العربية"
    override val english: String = "English"
    override val clearHistory: String = "مسح السجل"
    override val clearHistoryConfirm: String = "هل أنت متأكد من رغبتك في حذف جميع سجلات المعالجات؟"
    override val emptyHistory: String = "لا توجد سجلات معالجة حالياً"
    override val emptyQueue: String = "لا توجد مهام جارية في قائمة الانتظار"
    override val statusPaused: String = "موقوف مؤقتاً"
    override val convertNotice: String = "اختر طريقة التحويل بالأسفل. الطباعة أوضح للنص؛ اللقطة غالباً أصغر حجماً."
    override val languageSetting: String = "لغة الواجهة"
    override val themeSetting: String = "الوضع الداكن"
    override val storagePathSetting: String = "مسار الحفظ الإجباري"
    override val changePath: String = "تغيير المجلد"
    override val batteryOptTitle: String = "استثناء من توفير البطارية"
    override val batteryOptDesc: String = "يرجى إلغاء قيود توفير الطاقة لضمان استمرار المعالجات الكبيرة في الخلفية"
    override val batteryOptButton: String = "إلغاء قيود البطارية"
    override val cleanTempSuccess: String = "تم تنظيف جميع الملفات المؤقتة بنجاح"
    override val cleanTempFiles: String = "تنظيف الملفات المؤقتة"
    override val offlineDisclaimer: String = "معالجة محلياً 100% بدون إنترنت بحماية كاملة"

    override val notificationPauseResume: String = "إيقاف مؤقت / استئناف"
    override val notificationCancel: String = "إلغاء العملية"
    override val renameFile: String = "إعادة تسمية"
    override val newFileNamePrompt: String = "أدخل الاسم الجديد للملف:"
    override val fileRenamedSuccess: String = "تم تغيير اسم الملف بنجاح"
    override val fileRenameError: String = "تعذر تغيير اسم الملف"
    override val notificationPermissionTitle: String = "إذن الإشعارات"
    override val notificationPermissionBody: String = "يحتاج التطبيق إلى إذن الإشعارات لإبلاغك بتقدم عمليات معالجة الملفات في الخلفية وعند اكتمالها."
    override val notificationPermissionDeniedWarning: String = "الإشعارات معطلة: افتح الإعدادات لتفعيل إشعارات المعالجة"
    override val storagePermissionTitle: String = "صلاحية التخزين"
    override val storagePermissionBody: String = "يحتاج التطبيق صلاحية التخزين لحفظ الملفات المعالجة في مجلد التنزيلات على هذا الإصدار من أندرويد"
    override val openAppSettings: String = "إعدادات التطبيق"

    override val generalSettings: String = "الإعدادات العامة"
    override val storageAndNaming: String = "التخزين وتسمية الملفات"
    override val cumulativeSpaceSaved: String = "إجمالي المساحة الموفرة"
    override val autoNamingPattern: String = "نمط التسمية التلقائي"
    override val notificationsAndPermissions: String = "الإشعارات والأذونات"
    override val processingNotifications: String = "إشعارات تقدم المعالجة"
    override val openAppSettingsDesc: String = "فتح أذونات التطبيق في إعدادات النظام"
    override val advancedAndMaintenance: String = "متقدم وصيانة"
    override val showTechnicalBadges: String = "عرض شارة المعالج"
    override val showTechnicalBadgesDesc: String = "إظهار نوع المعالج (برمجي / عتاد) في شاشات المعالجة"

    override val searchInHistory: String = "بحث في السجل..."
    override val noSearchResults: String = "لا توجد نتائج مطابقة للبحث"
    override val changeFile: String = "(تغيير)"
    override val renameFailed: String = "فشل إعادة التسمية"

    // Error Messages
    override val errorVideoTranscodeFailed: String = "تعذر ضغط أو قراءة ملف الفيديو. يرجى التأكد من اختيار ملف فيديو سليم ومدعوم."
    override val videoSavedWithoutCompressionNotice: String = "تعذر ضغط الفيديو، تم حفظ نسخة بدون تغيير الحجم"
    override val errorProcessingTimeout: String = "توقفت عملية المعالجة، الرجاء المحاولة بملف آخر"
    override val errorAudioExtractFailed: String = "تعذر استخراج أو إعادة ترميز مسار الصوت من الفيديو المحدد."
    override val errorAudioTranscodeFailed: String = "فشلت عملية إعادة ترميز الملف الصوتي إلى صيغة AAC المضغوطة."
    override val errorPdfTextExtractionFailed: String = "تعذر استخراج النص. قد يكون المستند ممسوحًا ضوئيًا كصورة أو بدون طبقة نصية."
    override val errorInvalidPageRange: String = "نطاق صفحات غير صالح. اكتب أرقام صفحات بالصيغة الصحيحة، مثال: 1-3,5,8-10"
    override val warnSingleFileOperation: String = "تنبيه: هذه العملية تُنفَّذ على الملف الأول فقط. الدمج (Merge) هو العملية الوحيدة التي تعالج كل الملفات."
    override val errorPdfPasswordProtected: String = "هذا الملف محمي بكلمة مرور. يرجى إزالة الحماية من الملف أولاً (مثلاً عبر فتحه وإعادة حفظه بدون كلمة مرور) ثم إعادة المحاولة."
    override val errorLegacyOfficeFormat: String = "هذا التطبيق يدعم فقط الصيغ الحديثة (docx, xlsx, pptx). يمكنك فتح الملف القديم في Word/Excel وحفظه بصيغة حديثة ثم إعادة المحاولة"
    override val errorUnsupportedOfficeFormat: String = "صيغة الملف غير مدعومة"
    override val errorStoragePermissionDenied: String = "تعذر الحفظ لعدم منح صلاحية التخزين، يرجى منحها من إعدادات التطبيق"
    override val errorSaveFinalOutputFailed: String = "تعذر حفظ الملف النهائي"
    override val officeConvertDisclaimer: String = "ملاحظة: التحويل يستخرج النص الأساسي فقط ولا يحتفظ بالتنسيق، الجداول، أو الصور."
    override fun errorInsufficientCacheStorage(required: String): String = "مساحة الذاكرة المؤقتة (Cache) غير كافية لإكمال العملية (مطلوب حوالي $required)"
    override fun errorInsufficientStorage(required: String): String = "مساحة التخزين المتاحة في وجهة الحفظ غير كافية لإكمال العملية (مطلوب حوالي $required)"
    override fun errorDocxEmptyContent(fileName: String): String = "تعذر استخراج محتوى من ملف ($fileName)."
    override fun errorXlsxEmptyContent(fileName: String): String = "تعذر استخراج بيانات الجداول من ملف ($fileName)."
    override fun errorPptxEmptyContent(fileName: String): String = "تعذر استخراج الشرائح من العرض التقديمي ($fileName)."
    override fun errorOfficeConversionFailed(fileName: String, details: String): String = "تعذر قراءة أو تحويل الملف ($fileName): $details"
    override fun errorSaveToDownloadsFailed(details: String): String = "تعذر حفظ الملف في مجلد التنزيلات: $details"

    override val idleGreetings: List<String> = listOf(
        "Hello",       // English
        "مرحبا",       // Arabic
        "Bonjour",     // French
        "Hola",        // Spanish
        "Hallo",       // German
        "Ciao",        // Italian
        "こんにちは",    // Japanese
        "안녕하세요",     // Korean
        "Olá",         // Portuguese
        "Привет"       // Russian
    )

    override val errorImageToPdfAllFailed: String = "تعذر معالجة أي من الصور المحددة لتحويلها إلى PDF. يرجى التأكد من أن الصور بصيغة سليمة ومدعومة."
    override val errorImageProcessAllFailed: String = "تعذر معالجة أي من الصور المحددة. يرجى التأكد من أن الصور بصيغة سليمة ومدعومة."

    override val hwSwInfoTitle: String = "الفرق بين وضع العتاد (Hardware) والبرمجي (Software)"
    override val hwSwInfoHardwareBody: String = "⚡ وضع العتاد (Hardware Mode):\nيستخدم المسرعات المدمجة بشريحة المعالج (MediaCodec Chip) لمعالجة الفيديو والصوت بأقصى سرعة واستهلاك طاقة أقل."
    override val hwSwInfoSoftwareBody: String = "💻 الوضع البرمجي (Software Mode):\nيعتمد على المعالجة البرمجية الاحتياطية لضمان أعلى نسبة توافقية وجودة عند معالجة الملفات المعقدة أو غير المدعومة في شريحة الهاتف."

    override val filterAll: String = "الكل"
    override val filterVideo: String = "فيديو"
    override val filterAudio: String = "صوت"
    override val filterImage: String = "صور"
    override val filterDocument: String = "مستندات"
    override val shareOpenAppFailed: String = "فشل فتح تطبيق المشاركة"

    override val safeModeSwitchedNotice: String = "تم التبديل للوضع الآمن (قد لا يتم تقليل الحجم للحفاظ على الجودة)"

    override val imageTransparentPngNotice: String = "بعض الصور تحتوي خلفية شفافة وستُحفظ بصيغة PNG للحفاظ عليها"
    override val imageExifPrivacyNotice: String = "ملاحظة: يتم إزالة بيانات الموقع (EXIF) تلقائياً لحماية الخصوصية وتقليل الحجم."

    override val imageOutputFormatTitle: String = "صيغة الإخراج المطلوبة"
    override val imageFormatAutoTitle: String = "تلقائي"
    override val imageFormatAutoDesc: String = "اختيار الصيغة تلقائياً وفقاً للشفافية ونوع الملف"
    override val imageFormatJpgTitle: String = "JPG دائمًا"
    override val imageFormatJpgDesc: String = "مناسب للصور الفوتوغرافية وتوفير المساحة"
    override val imageFormatPngTitle: String = "PNG دائمًا"
    override val imageFormatPngDesc: String = "جودة فائقة مع الحفاظ على شفافية الخلفية"
    override val imageFormatWebpTitle: String = "WebP"
    override val imageFormatWebpDesc: String = "صيغة حديثة متطورة توفر حجماً أصغر وجودة عالية"

    override val errorFileDoesNotExist: String = "الملف غير موجود"
    override val errorCannotOpenFile: String = "تعذر فتح الملف"
    override val errorInvalidVideoFile: String = "ملف فيديو غير صالح"
    override val errorInvalidAudioFile: String = "ملف صوتي غير صالح أو تالف"
    override val audioBackgroundToggleLabel: String = "ضغط في الخلفية (موفر للبطارية)"
    override val audioBackgroundToggleDesc: String = "يعمل على دفعات صغيرة، ويتوقف تلقائياً عند انخفاض البطارية أو ارتفاع حرارة الجهاز"
    override val audioRequireChargingLabel: String = "فقط أثناء الشحن"
    override val audioFlacCompatWarning: String = "ملفات FLAC قد لا تُعالَج على بعض الأجهزة القديمة (أقدم من Android 8.0)"
    override val audioStepSelectFiles: String = "١. اختر الملفات"
    override val audioStepConfigureQuality: String = "٢. اضبط الجودة"
    override val audioStepAdditionalOptions: String = "٣. خيارات إضافية"
    override val videoHighRiskContainerWarning: String = "بعض أجهزة Android قد لا تدعم فتح ملفات AVI/MKV/WEBM هذه بشكل كامل؛ في حال الفشل سيُحفظ الملف كما هو دون تغيير"
    override val errorBatterySettingsUnavailable: String = "إعدادات البطارية غير متوفرة"

    override val moveUpDescription: String = "تحريك للأعلى"
    override val moveDownDescription: String = "تحريك للأسفل"
    override val dragHandleDescription: String = "اسحب لإعادة الترتيب"
    override val successDescription: String = "نجاح"
    override val hwSwInfoDescription: String = "معلومات العتاد/البرمجي"
}

object StringsEnglish : AppStrings {
    override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override val appName: String = "Vada"
    override val appSubtitle: String = "Fully Offline Media & Document Processor"
    override val homeTab: String = "Home"
    override val queueTab: String = "Queue"
    override val historyTab: String = "History"
    override val settingsTab: String = "Settings"

    override val videoSection: String = "Video Section"
    override val videoDesc: String = "Compress • Trim • Rotate • Mute — fully offline"
    override val audioSection: String = "Audio Section"
    override val audioDesc: String = "Extract, merge and compress audio files"
    override val imageSection: String = "Image Section"
    override val imageDesc: String = "Compress, convert and combine images to PDF"
    override val documentSection: String = "Document Section"
    override val documentDesc: String = "Compress, split, merge and extract text from PDF"
    override val convertSection: String = "Office to PDF"
    override val convertDesc: String = "Convert Word, Excel, PPT documents to PDF"
    override val conversionModeLabel: String = "Conversion method"
    override val conversionModePrintTitle: String = "Print (recommended)"
    override val conversionModePrintDesc: String = "Same as system “Print to PDF”. Clearer, often selectable text, balanced size after compression."
    override val conversionModeScreenshotTitle: String = "Internal screenshot"
    override val conversionModeScreenshotDesc: String = "Pages as compressed images. Often smaller files, but text isn’t selectable and zoom is softer."

    override val selectFile: String = "Select File"
    override val selectFiles: String = "Select Multiple Files"
    override val startProcessing: String = "Start Processing"
    override val cancel: String = "Cancel"
    override val confirm: String = "Confirm"
    override val pause: String = "Pause"
    override val resume: String = "Resume"
    override val delete: String = "Delete"
    override val clearAll: String = "Clear All"
    override val save: String = "Save"
    override val saveNamePrompt: String = "Output File Name:"
    override val savePathNotice: String = "File will be saved directly into Downloads/Vada"
    override val saveSuffixPrompt: String = "Shared naming suffix for each file:"
    override val saveMultiFileExample: String = "Output example:"
    override val saveMultiFileNotice: String = "A separate task is created per file, and each file is saved individually (no ZIP archive)."
    override val saveNameInvalidError: String = "Invalid name. The characters / \\ : * ? \" < > | are not allowed in file names."
    override val saveNameSanitizedNotice: String = "Forbidden characters will be removed, final name:"
    override val unsupportedFileTypeTitle: String = "Unsupported file type"
    override val unsupportedFileTypeMessage: String = "This file type is not supported. Supported types: video, audio, images, PDF, and Word/Excel/PowerPoint files."
    override val openFile: String = "Open File"
    override val shareFile: String = "Share"
    override val copyErrorLog: String = "Copy Error Log"
    override val ok: String = "OK"
    override val back: String = "Back"

    override val hwProcessorName: String = "Hardware Acceleration (On-Device Chip)"
    override val hwProcessorDesc: String = "Direct zero-recode passthrough for ultra performance"
    override val swProcessorName: String = "Software Processing (Device CPU)"
    override val swProcessorDesc: String = "High precision software engine for detailed compression"
    override val processorDialogTitle: String = "Processing Mode Details"
    override val processorDialogBody: String = "Vada operates 100% offline on your device.\n\n• Hardware Acceleration: Uses media chipsets for ultra-fast direct stream copy.\n• Software Processing: Software algorithms re-encode media with fine precision."
    override val videoFullReencodingNotice: String = "Full Video Re-encoding will be used"
    override val videoMethodRealEncoding: String = "Full Hardware Re-encoding"
    override val videoMethodPassthrough: String = "Direct Stream Passthrough (Remux)"

    override val presetLightTitle: String = "Light Compression"
    override val presetLightDesc: String = "Save 20% - 30% size with original pristine quality"
    override val presetMediumTitle: String = "Medium Compression"
    override val presetMediumDesc: String = "Balanced: Save 50% space with high visual quality"
    override val presetHeavyTitle: String = "Heavy Compression"
    override val presetHeavyDesc: String = "Maximum: Save 70% - 80% storage space"
    override val presetCustomTitle: String = "Custom Control"
    override val presetCustomDesc: String = "Manual compression parameters & bitrates"
    override val customControlsTitle: String = "Custom Settings"
    override val customControlsHint: String = "Drag to set quality, dimensions and bitrate yourself"
    override val customQualityLabel: String = "Image Quality"
    override val customAudioQualityLabel: String = "Audio Quality"
    override val customMaxDimensionLabel: String = "Max Resolution / Dimension"
    override val customBitrateLabel: String = "Video Bitrate"
    override val customNamingPatternLabel: String = "Custom Pattern"
    override val customNamingPatternHint: String = "e.g. {name}_small — use {name} as variable"
    override val customNamingPatternSave: String = "Save Pattern"
    override val compressionPresetLabel: String = "Select Compression Level:"

    override val muteAudioLabel: String = "Mute / Remove Audio Tracks"
    override val videoStepTrimRotate: String = "3. Trim & rotate (optional)"
    override val rotateLabel: String = "Rotate video"
    override val trimLabel: String = "Trim a portion of the video"
    override val trimStart: String = "Start"
    override val trimEnd: String = "End"
    override val muteAudioDesc: String = "Generate completely silent video"

    override val extractFromVideoLabel: String = "Extract Audio from Video"
    override val extractFromVideoDesc: String = "Pull audio stream directly from video file"

    override val combineToPdfLabel: String = "Combine Images into single PDF"
    override val removeExifLabel: String = "Strip EXIF Metadata"

    override val pdfOperationCompress: String = "Compress PDF Documents"
    override val pdfOperationSplit: String = "Split PDF File"
    override val pdfOperationMerge: String = "Merge Multiple PDF Files"
    override val pdfOperationExtractText: String = "Extract Selectable Text"
    override val splitAllPages: String = "Split each page into separate PDF (ZIP)"
    override val splitSpecificRange: String = "Extract specific range/pages"
    override val rangePlaceholder: String = "e.g. 1-3, 5, 8"

    override val convertNoticeTitle: String = "Real Text Layer"
    override val convertNoticeBody: String = "Converts files to vector PDF documents with selectable & searchable text."

    override val statusIdle: String = "Vada Ready"
    override val statusProcessing: String = "Processing..."
    override val statusSuccess: String = "Completed ✅"
    override val statusError: String = "Error ❌"
    override val videoCompressionSkippedNotice: String = "Could not compress video, saved copy without size reduction"
    override val videoCompressionSkippedShort: String = "Saved without compression ⚠️"
    override val compressionOutcomeSuccess: String = "Compressed ✅"
    override val compressionOutcomeMarginal: String = "Marginal Compression ⚠️"
    override val compressionOutcomeNone: String = "Not Compressed ❌"

    override val queueEmptyTitle: String = "No Active Tasks"
    override val queueEmptySubtitle: String = "Start a process from any section to see tasks here"
    override val activeTaskHeader: String = "Active Task"
    override val pendingTasksHeader: String = "Pending Queue"

    override val historyEmptyTitle: String = "History Empty"
    override val historyEmptySubtitle: String = "Completed tasks and outputs will appear here"
    override val historyClearConfirmTitle: String = "Clear All History"
    override val historyClearConfirmBody: String = "Are you sure you want to clear processing history? Output files remain saved."

    override val languageSection: String = "App Language"
    override val languageArabic: String = "العربية (RTL)"
    override val languageEnglish: String = "English (LTR)"
    override val themeSection: String = "Theme & Aesthetics"
    override val darkTheme: String = "Dark Theme (Deep Luxury Ocean)"
    override val lightTheme: String = "Light Theme"
    override val saveFolderSection: String = "Output Directory"
    override val currentFolderLabel: String = "Current Path:"
    override val changeFolderButton: String = "Choose Custom Folder (SAF)"
    override val batteryOptimizationTitle: String = "Battery Optimization"
    override val batteryOptimizationDesc: String = "Exclude app from battery restrictions to keep long processing running smoothly in background."
    override val fixBatteryButton: String = "Disable Battery Restrictions"
    override val cleanTempFilesTitle: String = "Temporary Cache"
    override val cleanTempFilesDesc: String = "Clean temporary files generated during processing"
    override val cleanTempButton: String = "Clean Temporary Files Now"
    override val tempFilesCleanedToast: String = "All temporary files cleared successfully"

    override val confirmStartTitle: String = "Confirm Start"
    override val confirmStartBody: String = "Do you want to start processing selected files with current options?"
    override val confirmCancelTitle: String = "Cancel Task"
    override val confirmCancelBody: String = "Are you sure you want to cancel this operation? Temp data will be removed."
    override val confirmLanguageChangeTitle: String = "Change Language"
    override val confirmLanguageChangeBody: String = "UI layout direction will update immediately. Continue?"
    override val confirmClearSelectionTitle: String = "Clear Selected Files"
    override val confirmClearSelectionBody: String = "This will clear your currently selected file. Do you want to continue?"

    override val arabic: String = "العربية"
    override val english: String = "English"
    override val clearHistory: String = "Clear History"
    override val clearHistoryConfirm: String = "Are you sure you want to clear processing history?"
    override val emptyHistory: String = "No processing history records yet"
    override val emptyQueue: String = "No active tasks in the processing queue"
    override val statusPaused: String = "Paused"
    override val convertNotice: String = "Pick a method below. Print is clearer for text; screenshot is often smaller."
    override val languageSetting: String = "App Language"
    override val themeSetting: String = "Dark Theme"
    override val storagePathSetting: String = "Output Folder Path"
    override val changePath: String = "Change Directory"
    override val batteryOptTitle: String = "Battery Optimization"
    override val batteryOptDesc: String = "Exclude app from battery restrictions to keep background processing active"
    override val batteryOptButton: String = "Disable Restrictions"
    override val cleanTempSuccess: String = "All temporary files cleaned successfully"
    override val cleanTempFiles: String = "Clean Temporary Cache"
    override val offlineDisclaimer: String = "100% Secure Local Offline Processing"

    override val notificationPauseResume: String = "Pause / Resume"
    override val notificationCancel: String = "Cancel Operation"
    override val renameFile: String = "Rename"
    override val newFileNamePrompt: String = "Enter new file name:"
    override val fileRenamedSuccess: String = "File renamed successfully"
    override val fileRenameError: String = "Failed to rename file"
    override val notificationPermissionTitle: String = "Notification Permission"
    override val notificationPermissionBody: String = "The app needs notification permission to inform you of background processing progress and completion."
    override val notificationPermissionDeniedWarning: String = "Notifications disabled: Open settings to enable processing alerts"
    override val storagePermissionTitle: String = "Storage Permission"
    override val storagePermissionBody: String = "The app needs storage permission to save processed files to the Downloads folder on this Android version."
    override val openAppSettings: String = "App Settings"

    override val generalSettings: String = "General Settings"
    override val storageAndNaming: String = "Storage & Naming"
    override val cumulativeSpaceSaved: String = "Cumulative Space Saved"
    override val autoNamingPattern: String = "Auto-Naming Pattern"
    override val notificationsAndPermissions: String = "Notifications & Permissions"
    override val processingNotifications: String = "Processing Notifications"
    override val openAppSettingsDesc: String = "Open App Permissions in System Settings"
    override val advancedAndMaintenance: String = "Advanced & Maintenance"
    override val showTechnicalBadges: String = "Show Processor Badge"
    override val showTechnicalBadgesDesc: String = "Display the processor type (hardware / software) on processing screens"

    override val searchInHistory: String = "Search in history..."
    override val noSearchResults: String = "No matching results found"
    override val changeFile: String = "(Change)"
    override val renameFailed: String = "Rename failed"

    // Error Messages
    override val errorVideoTranscodeFailed: String = "Failed to compress or read the video file. Please ensure a valid and supported video file is selected."
    override val videoSavedWithoutCompressionNotice: String = "Could not compress the video; a copy was saved without size reduction"
    override val errorProcessingTimeout: String = "Processing operation timed out, please try another file"
    override val errorAudioExtractFailed: String = "Failed to extract or re-encode audio track from the selected video."
    override val errorAudioTranscodeFailed: String = "Failed to re-encode the audio file to compressed AAC format."
    override val errorPdfTextExtractionFailed: String = "Failed to extract text. The document might be scanned as an image or lacks a text layer."
    override val errorInvalidPageRange: String = "Invalid page range. Use a valid format, e.g. 1-3,5,8-10"
    override val warnSingleFileOperation: String = "Note: this operation runs on the first file only. Merge is the only operation that processes all files."
    override val errorPdfPasswordProtected: String = "This file is password-protected. Please remove the protection first (e.g. open it and re-save without a password), then try again."
    override val errorLegacyOfficeFormat: String = "This app only supports modern formats (docx, xlsx, pptx). Please open the legacy file in Word/Excel, save as a modern format, and try again."
    override val errorUnsupportedOfficeFormat: String = "Unsupported file format"
    override val errorStoragePermissionDenied: String = "Failed to save due to missing storage permission. Please grant it in app settings."
    override val errorSaveFinalOutputFailed: String = "Failed to save final output file"
    override val officeConvertDisclaimer: String = "Note: Conversion extracts main text and layout to vector PDF format."
    override fun errorInsufficientCacheStorage(required: String): String = "Insufficient cache storage to complete the operation (required approx $required)"
    override fun errorInsufficientStorage(required: String): String = "Insufficient storage space at destination to complete the operation (required approx $required)"
    override fun errorDocxEmptyContent(fileName: String): String = "Failed to extract content from file ($fileName)."
    override fun errorXlsxEmptyContent(fileName: String): String = "Failed to extract spreadsheet data from file ($fileName)."
    override fun errorPptxEmptyContent(fileName: String): String = "Failed to extract slides from presentation ($fileName)."
    override fun errorOfficeConversionFailed(fileName: String, details: String): String = "Failed to read or convert file ($fileName): $details"
    override fun errorSaveToDownloadsFailed(details: String): String = "Failed to save file in Downloads directory: $details"

    override val idleGreetings: List<String> = listOf(
        "Hello",       // English
        "مرحبا",       // Arabic
        "Bonjour",     // French
        "Hola",        // Spanish
        "Hallo",       // German
        "Ciao",        // Italian
        "こんにちは",    // Japanese
        "안녕하세요",     // Korean
        "Olá",         // Portuguese
        "Привет"       // Russian
    )

    override val errorImageToPdfAllFailed: String = "Failed to process any of the selected images into a PDF. Please make sure the images are in a valid, supported format."
    override val errorImageProcessAllFailed: String = "Failed to process any of the selected images. Please make sure the images are in a valid, supported format."

    override val hwSwInfoTitle: String = "Hardware vs Software Mode Difference"
    override val hwSwInfoHardwareBody: String = "⚡ Hardware Mode:\nUses the accelerators built into the processor chip (MediaCodec Chip) to process video and audio at maximum speed with lower power consumption."
    override val hwSwInfoSoftwareBody: String = "💻 Software Mode:\nRelies on fallback software processing to ensure the highest compatibility and quality when handling complex files or formats not supported by the phone's chip."

    override val filterAll: String = "All"
    override val filterVideo: String = "Video"
    override val filterAudio: String = "Audio"
    override val filterImage: String = "Image"
    override val filterDocument: String = "Documents"
    override val shareOpenAppFailed: String = "Failed to open the share app"

    override val safeModeSwitchedNotice: String = "Switched to safe mode (size may not be reduced in order to preserve quality)"

    override val imageTransparentPngNotice: String = "Some images have a transparent background and will be saved as PNG to preserve it"
    override val imageExifPrivacyNotice: String = "Note: Location data (EXIF) is automatically removed to protect your privacy and reduce size."

    override val imageOutputFormatTitle: String = "Desired Output Format"
    override val imageFormatAutoTitle: String = "Automatic"
    override val imageFormatAutoDesc: String = "Automatically choose the format based on transparency and file type"
    override val imageFormatJpgTitle: String = "Always JPG"
    override val imageFormatJpgDesc: String = "Suitable for photos and saving space"
    override val imageFormatPngTitle: String = "Always PNG"
    override val imageFormatPngDesc: String = "Superior quality while preserving background transparency"
    override val imageFormatWebpTitle: String = "WebP"
    override val imageFormatWebpDesc: String = "A modern, advanced format offering smaller size and high quality"

    override val errorFileDoesNotExist: String = "File does not exist"
    override val errorCannotOpenFile: String = "Cannot open file"
    override val errorInvalidVideoFile: String = "Invalid video file"
    override val errorInvalidAudioFile: String = "Invalid or corrupted audio file"
    override val audioBackgroundToggleLabel: String = "Compress in background (battery-friendly)"
    override val audioBackgroundToggleDesc: String = "Runs in small chunks, pausing automatically on low battery or device overheating"
    override val audioRequireChargingLabel: String = "Only while charging"
    override val audioFlacCompatWarning: String = "FLAC files may not process on some older devices (pre-Android 8.0)"
    override val audioStepSelectFiles: String = "1. Select files"
    override val audioStepConfigureQuality: String = "2. Configure quality"
    override val audioStepAdditionalOptions: String = "3. Additional options"
    override val videoHighRiskContainerWarning: String = "Some Android devices may not fully support opening AVI/MKV/WEBM files; if processing fails, the file will be saved unchanged"
    override val errorBatterySettingsUnavailable: String = "Battery settings unavailable"

    override val moveUpDescription: String = "Move Up"
    override val moveDownDescription: String = "Move Down"
    override val dragHandleDescription: String = "Drag to reorder"
    override val successDescription: String = "Success"
    override val hwSwInfoDescription: String = "HW/SW Info"
}
