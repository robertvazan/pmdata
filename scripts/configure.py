# This script generates and updates project configuration files.

# Run this script with rvscaffold in PYTHONPATH
import rvscaffold as scaffold

class Project(scaffold.Java):
    def script_path_text(self): return __file__
    def repository_name(self): return 'pmdata'
    def pretty_name(self): return 'PMData'
    def pom_description(self): return "Framework for reactive data-driven and data science websites."
    def inception_year(self): return 2020
    def jdk_version(self): return 17
    def has_javadoc(self): return False
    def stagean_annotations(self): return True
    
    def dependencies(self):
        yield from super().dependencies()
        yield self.use_noexception()
        yield self.use_hookless()
        yield self.use_pushmode()
        yield self.use_pmsite()
        yield self.use_meerkatwidgets()
        yield self.use_foxcache()
        yield self.use_guava()
        yield self.use_commons_lang()
        yield self.use_slf4j()
        yield self.use('org.jfree:jfreechart:1.5.3')
        # Batik is used to render charts into SVG.
        batik_version = '1.14'
        yield self.use(f'org.apache.xmlgraphics:batik-dom:{batik_version}')
        yield self.use(f'org.apache.xmlgraphics:batik-svggen:{batik_version}')
        yield self.use(f'org.apache.xmlgraphics:batik-awt-util:{batik_version}')
        yield self.use('com.github.haifengl:smile-plot:2.6.0')
        yield self.use_junit()
        yield self.use_slf4j_test()
    
    def javadoc_links(self):
        yield 'https://stagean.machinezoo.com/javadoc/'
        # PMSite does not have javadoc yet.
        # JFreeChart not linked, because automatic modules are not supported by javadoc.
        # Smile plot not linked, because it is not a module yet.

Project().generate()
