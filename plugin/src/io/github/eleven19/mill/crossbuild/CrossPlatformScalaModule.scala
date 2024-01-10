package io.github.eleven19.mill.crossbuild

import mill._
import mill.scalalib._

trait CrossPlatformScalaModule extends PlatformScalaModule with CrossScalaModule {
    protected def scalaVersionDirectoryNames(scalaVersion: String): Seq[String] = scalaVersionDirectoryNames

    def extraScalaVersionSuffixes(scalaVersion: String): Seq[String] =
        partialVersion(scalaVersion) match {
            case Some((3, _))  => Seq("2.12+", "2.13+")
            case Some((2, 13)) => Seq("2", "2.12+", "2.13+", "2.12-2.13")
            case Some((2, 12)) => Seq("2", "2.12+", "2.11-2.12", "2.12-2.13")
            case Some((2, 11)) => Seq("2", "2.11-2.12")
            case _             => Seq.empty
        }

    def crossPlatformSourceSuffixes(scalaVersion: String, srcFolderName: String) =
        for {
            versionSuffix <-
                Seq("") ++ scalaVersionDirectoryNames(scalaVersion) ++ extraScalaVersionSuffixes(scalaVersion)
            platformSuffix <- Seq("") ++ platform.suffixes
        } yield (versionSuffix, platformSuffix) match {
            case ("", "")     => srcFolderName
            case ("", suffix) => s"$srcFolderName-$suffix"
            case (suffix, "") => s"$srcFolderName-$suffix"
            case (vs, ps)     => s"$srcFolderName-$vs-$ps"
        }

    def crossPlatformRelativeSourcePaths(scalaVersion: String, srcFolderName: String): Seq[os.RelPath] =
        for {
            versionSuffix <-
                Seq("") ++ scalaVersionDirectoryNames(scalaVersion) ++ extraScalaVersionSuffixes(scalaVersion)
            platformSuffix <- Seq("") ++ platform.suffixes
        } yield (versionSuffix, platformSuffix) match {
            case ("", "")     => os.rel / srcFolderName
            case ("", suffix) => os.rel / suffix / srcFolderName
            case (suffix, "") => os.rel / s"$srcFolderName-$suffix"
            case (vs, ps)     => os.rel / ps / s"$srcFolderName-$vs"
        }

    def crossPlatformSources: T[Seq[PathRef]] = T.sources {
        val scalaVers = scalaVersion()
        platformFolderMode() match {
            case Platform.FolderMode.UseSuffix =>
                crossPlatformSourceSuffixes(scalaVers, "src").map(suffix => PathRef(millSourcePath / suffix))
            case Platform.FolderMode.UseNesting =>
                crossPlatformRelativeSourcePaths(scalaVers, "src").map(subPath => PathRef(millSourcePath / subPath))
            case Platform.FolderMode.UseBoth =>
                (crossPlatformSourceSuffixes(scalaVers, "src").map(suffix => PathRef(millSourcePath / suffix)) ++
                    crossPlatformRelativeSourcePaths(scalaVers, "src").map(subPath =>
                        PathRef(millSourcePath / subPath)
                    )).distinct
        }
    }

    def partialVersion(version: String): Option[(Int, Int)] = {
        val partial = version.split('.').take(2)
        for {
            major    <- partial.headOption
            majorInt <- major.toIntOption
            minor    <- partial.lastOption
            minorInt <- minor.toIntOption
        } yield (majorInt, minorInt)
    }

    def platformSpecificModuleDeps:         Seq[CrossPlatform] = Seq.empty
    def platformSpecificCompiledModuleDeps: Seq[CrossPlatform] = Seq.empty

    override def moduleDeps = super.moduleDeps ++ platformSpecificModuleDeps.flatMap { case p =>
        p.childPlatformModules(platform)
    }
    override def compileModuleDeps =
        super.compileModuleDeps ++ platformSpecificCompiledModuleDeps.flatMap(_.childPlatformModules(platform))

    //     container.moduleDeps.map(innerModule).asInstanceOf[Seq[this.type]]
    // override def compileModuleDeps = super.compileModuleDeps ++
    //     container.compileModuleDeps.map(innerModule).asInstanceOf[Seq[this.type]]

    // def innerModule(module:CrossPlatformModule) = module match {}

    def platformFolderMode: T[Platform.FolderMode] = T(Platform.FolderMode.UseNesting)
    def platform:           Platform
    def knownPlatforms:     T[Seq[Platform]]       = T(Platform.all.toSeq)

    override def sources: mill.define.Target[Seq[mill.api.PathRef]] = T.sources {
        crossPlatformSources()
    }
}
