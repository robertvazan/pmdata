# This script generates and updates project configuration files.

# Run this script with rvscaffold in PYTHONPATH
import rvscaffold as scaffold

class Project(scaffold.Java):
    def script_path_text(self): return __file__
    def repository_name(self): return 'meerkatwidgets'
    def pretty_name(self): return 'Meerkat Widgets'
    def pom_description(self): return "Collection of simple reactive HTML widgets for PushMode apps."
    def inception_year(self): return 2023
    def jdk_version(self): return 17
    def has_javadoc(self): return False
    def stagean_annotations(self): return True
    def has_website(self): return False
    
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
