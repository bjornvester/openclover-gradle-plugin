package com.github.bjornvester

import com.atlassian.clover.CloverDatabase
import com.atlassian.clover.CloverDatabaseSpec
import com.github.bjornvester.OpenCloverPlugin.Companion.logDbDirs
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*

@CacheableTask
open class MergeDbTask : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    var inputFiles: ListProperty<RegularFile> = project.objects.listProperty(RegularFile::class.java)

    @get:OutputDirectory
    var dbDir: DirectoryProperty = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("clover-db-merged"))

    init {
        group = BasePlugin.BUILD_GROUP
        description = "Merges OpenClover databases."
    }

    @TaskAction
    fun doMerge() {
        val dbList = ArrayList<CloverDatabaseSpec>()
        inputFiles.get().forEach {
            if (it.asFile.exists()) {
                project.logger.info("Adding OpenClover database: $it")
                dbList.add(CloverDatabaseSpec(it.asFile.path))
            }
        }

        project.delete(dbDir)
        project.mkdir(dbDir)
        if (dbList.size == 1) {
            project.copy {
                from(dbList.single().initString)
                into(dbDir)
            }
        } else {
            CloverDatabase.merge(dbList, dbDir.file("clover.db").get().toString())
        }

        logDbDirs(this, inputFiles, dbDir)
    }
}
