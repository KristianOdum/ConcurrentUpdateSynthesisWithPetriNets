# All arc objects take node and transitions as objects
class Inbound_Arc:
    def __init__(self, source, transition,arctype,weight):
        self.source = source
        self.transition = transition
        self.arctype = arctype
        self.weight = weight

    def to_file(self):
        return (
                    "    <arc id=\"{} to {}\" inscription=\"[0,inf)\" nameOffsetX=\"0.0\" nameOffsetY=\"0.0\" source=\"{}\" target=\"{}\" type=\"{}\" weight=\"{}\">\n"
                    .format(self.source.notation, self.transition.notation, self.source.notation,
                            self.transition.notation, self.arctype, self.weight)
                    + "    </arc>\n")


class Outbound_Arc:
    def __init__(self, transition, target):
        self.transition = transition
        self.target = target

    def to_file(self):
        return (
                    "    <arc id=\"{} to {}\" inscription=\"1\" nameOffsetX=\"0.0\" nameOffsetY=\"0.0\" source=\"{}\" target=\"{}\" type=\"normal\" weight=\"1\">\n"
                    .format(self.transition.notation, self.target.notation, self.transition.notation,
                            self.target.notation)
                    + "    </arc>\n")


class Full_Arc:
    def __init__(self, source, target, transition):
        self.source = source
        self.target = target
        self.transition = transition

    def to_file(self):
        return (
                    "    <arc id=\"{} to {}\" inscription=\"[0,inf)\" nameOffsetX=\"0.0\" nameOffsetY=\"0.0\" source=\"{}\" target=\"{}\" type=\"timed\" weight=\"1\">\n"
                    .format(self.source.notation, self.transition.notation, self.source.notation,
                            self.transition.notation)
                    + "    </arc>\n"
                    + "    <arc id=\"{} to {}\" inscription=\"1\" nameOffsetX=\"0.0\" nameOffsetY=\"0.0\" source=\"{}\" target=\"{}\" type=\"normal\" weight=\"1\">\n"
                    .format(self.transition.notation, self.target.notation, self.transition.notation,
                            self.target.notation)
                    + "    </arc>\n")

    def info(self):
        print("Source: {}, Target: {}, Transition: {}"
              .format(self.source.notation, self.target.notation, self.transition.notation))

