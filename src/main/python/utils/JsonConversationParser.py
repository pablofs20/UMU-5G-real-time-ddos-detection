import json
class JsonConversationParser:
    def __init__(self, data):
        # json as string
        self.data = data

        # get different values
        self.uplinkIP = data['data'][2]['uplinkIP']
        self.downlinkIP = data['data'][2]['downlinkIP']
        self.uplinkPort = data['data'][2]['uplinkPort']
        self.downlinkPort = data['data'][2]['downlinkPort']

    # return features field
    def getFeatures(self):
        return self.data['data'][3]['features'].split(',')

    # delete features, add attack tag and return as json
    def addNewFeatures(self, attack, time):
        del self.data['data'][3]['features']
        self.data['data'][3]['ml'] = {
            'attack': 'true' if attack else 'false',
            'time': time
        }

        return json.dumps(self.data)
