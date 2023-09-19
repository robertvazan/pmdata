# This script generates and updates project configuration files.

# Run this script with rvscaffold in PYTHONPATH
import rvscaffold as scaffold

class Project(scaffold.Java):
    def script_path_text(self): return __file__
    def repository_name(self): return 'remorabindings'
    def pretty_name(self): return 'Remora Bindings'
    def pom_description(self): return "Two-way data bindings used to bind UI widgets to data sources in pushmode apps."
    def inception_year(self): return 2023
    def jdk_version(self): return 17
    def has_javadoc(self): return False
    def stagean_annotations(self): return True
    
    def dependencies(self):
        yield from super().dependencies()
        yield self.use_noexception()
        yield self.use_hookless_prefs()
        yield self.use_pmsite()
        yield self.use_junit()
    
    def javadoc_links(self):
        yield 'https://stagean.machinezoo.com/javadoc/'
        yield 'https://hookless.machinezoo.com/javadocs/prefs/'
        # PMSite does not have javadoc yet.

Project().generate()
