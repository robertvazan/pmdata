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
        yield self.use_pmsite()
        yield self.use_ladybugformatters()
        yield self.use_remorabindings()
        yield self.use_streamex()
        # Used to serialize cache content output.
        yield self.use('com.esotericsoftware:kryo:5.2.0')
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
        yield 'https://noexception.machinezoo.com/javadoc/'
        yield 'https://hookless.machinezoo.com/javadocs/core/'
        yield 'https://pushmode.machinezoo.com/javadoc/'
        # PMSite does not have javadoc yet.
        # JFreeChart and Kryo not linked, because automatic modules are not supported by javadoc.
        # Smile plot not linked, because it is not a module yet.

Project().generate()
