# ===================================================================
# R8 / ProGuard Configuration for TH Testes
# ===================================================================

# Achata a estrutura de pacotes movendo as classes ofuscadas para o pacote raiz
-repackageclasses ''
-allowaccessmodification

# Garante nomes consistentes
-dontusemixedcaseclassnames

# Preserva métodos nativos (JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserva campos CREATOR para classes que implementam Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Preserva métodos values() e valueOf() em Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserva atributos necessários para Compose, Coroutines e depuração de stacktrace
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Ofusca o nome dos arquivos fonte nos rastros de erro
-renamesourcefileattribute SourceFile

# Suprime avisos desnecessários de dependências externas
-dontwarn **
