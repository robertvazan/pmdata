# This script generates and updates project configuration files.

# Run this script with rvscaffold in PYTHONPATH
import rvscaffold as scaffold

class Project(scaffold.Java):
    def script_path_text(self): return __file__
    def repository_name(self): return 'ladybugformatters'
    def pretty_name(self): return 'Ladybug Formatters'
    def pom_description(self): return "Collection of convenient formatters for projects using hookless and pushmode."
    def inception_year(self): return 2023
    def jdk_version(self): return 17
    def has_javadoc(self): return False
    def stagean_annotations(self): return True
    
    def dependencies(self):
        yield from super().dependencies()
        yield self.use_hookless_time()
        yield self.use_pushmode()
        yield self.use_junit()
    
    def javadoc_links(self):
        yield 'https://stagean.machinezoo.com/javadoc/'
        yield 'https://hookless.machinezoo.com/javadocs/core/'
        yield 'https://hookless.machinezoo.com/javadocs/time/'
        yield 'https://pushmode.machinezoo.com/javadoc/'

Project().generate()
