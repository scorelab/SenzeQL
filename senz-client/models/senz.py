class Senz(object):
    """
    Keep senz message attributes in here. Senz parser will be generates this
    type of object when datagram recives.
    """
    def __init__(self, type=None, sender=None, receiver=None, attributes={},
                 signature=None):
        """
        Initlize senz object with defaly values from here

        Args:
            1. type - GET, SHARE, DATA, PUT
            2. attributes - dictionaty to keep senz attributes
            3. sender - sender
            4. receiver - senz receiver
            5. signature - digital signarue of the senz
        """
        self.type = type
        self.sender = sender
        self.receiver = receiver
        self.attributes = attributes
        self.signature = signature
