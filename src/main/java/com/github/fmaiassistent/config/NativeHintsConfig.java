package com.github.fmaiassistent.config;

import com.github.fmaiassistent.domain.entity.ChatMessageEntity;
import com.github.fmaiassistent.domain.entity.ChatSessionEntity;
import com.github.fmaiassistent.domain.entity.ClubEntity;
import com.github.fmaiassistent.domain.entity.CompetitionEntity;
import com.github.fmaiassistent.domain.entity.LoadMetadataEntity;
import com.github.fmaiassistent.domain.entity.PlayerEntity;
import com.github.fmaiassistent.repository.PlayerFilterCriteria;
import com.github.fmaiassistent.web.ui.SavedChatPrompt;
import com.github.fmaiassistent.web.ui.SavedPlayerView;
import com.github.fmaiassistent.windows.WindowsProcessReader;
import liquibase.change.ColumnConfig;
import liquibase.change.core.*;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.List;

@ImportRuntimeHints(NativeHintsConfig.LiquibaseNativeHints.class)
@Configuration(proxyBeanMethods = false)
public class NativeHintsConfig {

    private static final MemberCategory[] REFLECT_CATEGORIES = {
            MemberCategory.DECLARED_FIELDS,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS
    };

    private static final List<Class<?>> JPA_ENTITIES = List.of(
            PlayerEntity.class,
            ClubEntity.class,
            CompetitionEntity.class,
            ChatSessionEntity.class,
            ChatMessageEntity.class,
            LoadMetadataEntity.class
    );

    private static final List<Class<?>> JNA_STRUCTURES = List.of(
            WindowsProcessReader.MemoryBasicInformation.class,
            WindowsProcessReader.ModuleEntry32.class,
            WindowsProcessReader.ModuleInfo.class
    );

    static class LiquibaseNativeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(LoadDataChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

            hints.reflection().registerType(CreateTableChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(AddColumnChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(ModifyDataTypeChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(DropColumnChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(DropTableChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(CreateIndexChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(AddForeignKeyConstraintChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(AddNotNullConstraintChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(InsertDataChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(UpdateDataChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(RawSQLChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(SQLFileChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(CreateViewChange.class, MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
            hints.reflection().registerType(LoadDataColumnConfig.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(ColumnConfig.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(SavedPlayerView.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(SavedChatPrompt.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(PlayerFilterCriteria.class, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);

            JPA_ENTITIES.forEach(entity ->
                    hints.reflection().registerType(entity, REFLECT_CATEGORIES));

            JNA_STRUCTURES.forEach(struct ->
                    hints.reflection().registerType(struct, REFLECT_CATEGORIES));

            hints.reflection().registerType(org.springframework.aot.hint.TypeReference.of("com.github.fmaiassistent.windows.WindowsProcessReader$Kernel32"), MemberCategory.INVOKE_PUBLIC_METHODS);

            hints.reflection().registerType(com.sun.jna.Native.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.Memory.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.Pointer.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.Structure.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.WString.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.ptr.LongByReference.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.ptr.IntByReference.class, REFLECT_CATEGORIES);
            hints.reflection().registerType(com.sun.jna.win32.W32APIOptions.class, REFLECT_CATEGORIES);

            hints.resources().registerPattern("db/changelog/**");
            hints.resources().registerPattern("db/data/**");
        }
    }
}
