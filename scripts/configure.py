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
        yield self.use_closeablescope()
        yield self.use_noexception()
        yield self.use_pushmode()
        yield self.use_pmsite()
        yield self.use_ladybugformatters()
        yield self.use_remorabindings()
        yield self.use_streamex()
        yield self.use_fastutil()
        yield self.use_junit()
    
    def javadoc_links(self):
        yield 'https://stagean.machinezoo.com/javadoc/'
        yield 'https://closeablescope.machinezoo.com/javadoc/'
        yield 'https://pushmode.machinezoo.com/javadoc/'
        # PMSite does not have javadoc yet.
        # Remora Bindings do not have javadoc yet.

Project().generate()
