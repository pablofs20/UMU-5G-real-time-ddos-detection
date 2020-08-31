import time
import numpy as np
import pandas as pd
from sklearn.mixture import GaussianMixture
from sklearn.neural_network import MLPRegressor
from sklearn.preprocessing import MinMaxScaler
from utils.JsonConversationParser import JsonConversationParser


class MLModel:
    def __init__(self, training_data):
        self.training_data = training_data
        self.__columns_names = ["Uplink_IP", "Downlink_IP", "Uplink_port", "Downlink_port",
                                "Duration", "Packets_up", "Packets_down", "Bytes_up", "Bytes_down",
                                "Max_ps_up", "Min_ps_up", "Ave_ps_up",
                                "Max_ps_do", "Min_ps_do", "Ave_ps_do",
                                "Max_TCP_up", "Min_TCP_up", "Ave_TCP_up",
                                "Max_TCP_do", "Min_TCP_do", "Ave_TCP_do",
                                "Max_TTL_up", "Min_TTL_up", "Ave_TTL_up",
                                "Max_TTL_do", "Min_TTL_do", "Ave_TTL_do",
                                "FIN_up", "SYN_up", "RST_up", "PSH_up", "ACK_up", "URG_up",
                                "FIN_do", "SYN_do", "RST_do", "PSH_do", "ACK_do", "URG_do",
                                "chgcipher_up", "alert_up", "handshake_up", "appdata_up", "heartbeat_up",
                                "chgcipher_do", "alert_do", "handshake_do", "appdata_do", "heartbeat_do",
                                "Count", "Srv_count", "Same_src_rate", "Diff_src_rate", "Dst_host_count",
                                "Dst_host_srv_count", "Dst_host_same_srv_rate", "Dst_host_diff_srv_rate"]

        self.__feat_columns_names = None
        self.final_data = None
        self.centers = None
        self.gmm = None
        self.nn = None
        self.std = None
        self.scaler = None
        self.__thresh = None
        self._sender = None
        self._log_File = None

    def start(self):
        # Read training data and convert to pandas
        data = pd.read_csv(self.training_data, delimiter=',')

        # rename dataframe
        data.columns = self.__columns_names
        data[self.__columns_names[2:]] = data[self.__columns_names[2:]].astype(float)

        # transform NA values to median
        data.fillna(data.median(), inplace=True)

        # cols to remove
        useless = ["Min_ps_up", "Min_ps_do", "Max_TCP_up", "Max_TCP_do",
                   "Min_TCP_do", "Max_TTL_up", "Min_TTL_up", "Ave_TTL_up", "Max_TTL_do",
                   "Min_TTL_do", "Ave_TTL_do", "RST_up", "URG_up", "RST_do", "ACK_do",
                   "URG_do", "heartbeat_do"]

        self.__feat_columns_names = [col for col in self.__columns_names if col not in useless]
        self.__feat_columns_names = self.__feat_columns_names[4:]

        # get rid of useless columns
        data.drop(useless, axis=1, inplace=True)

        # get rid of values with errors
        data = data[data["Duration"] >= 0].reset_index(drop=True)

        self.scaler = MinMaxScaler()
        self.scaler.fit(data.iloc[:, 4:])
        # Normalize dataset
        data = pd.DataFrame(self.scaler.transform(data.iloc[:, 4:]))

        # train
        data = data.iloc[0:100000, :].reset_index(drop=True)

        # GMM model
        self.k = 3
        self.gmm = GaussianMixture(n_components=self.k, covariance_type='diag', random_state=0)
        self.gmm.fit(data)

        # get std
        self.std = data.std()

        # get centers
        self.centers = self.gmm.means_

        clus = pd.Series(self.gmm.predict(data), name='Clusters')
        cluster = pd.concat([data, clus], axis=1)

        # autoencoder
        self.nn = MLPRegressor(activation='identity',
                               alpha=0.001,
                               hidden_layer_sizes=(3,),
                               max_iter=400,
                               random_state=0,
                               solver='lbfgs')

        # train
        hist = MLModel.__histo(cluster, self.centers, self.std, 36, 3, 1)
        print(hist)
        self.nn.fit(hist, hist)
        hpred = pd.DataFrame(self.nn.predict(hist))
        rec = np.sqrt(np.sum((hist - hpred) ** 2, axis=1))
        self.um = np.mean(rec) + 0.01 * np.std(rec)

        print("Init Complete")

    def check_attack(self, data):
        parsers = []
        features_conversations = []
        for conversation in data:
            p = JsonConversationParser(conversation)
            parsers.append(p)
            features_conversations.append(p.getFeatures())

        if len(features_conversations) == 0:
            return []

        df = pd.DataFrame(features_conversations)

        # time
        time_start = int(round(time.time() * 1000))

        df.columns = self.__columns_names
        attacks = df

        # cols to remove
        useless = ["Min_ps_up", "Min_ps_do", "Max_TCP_up", "Max_TCP_do",
                   "Min_TCP_do", "Max_TTL_up", "Min_TTL_up", "Ave_TTL_up", "Max_TTL_do",
                   "Min_TTL_do", "Ave_TTL_do", "RST_up", "URG_up", "RST_do", "ACK_do",
                   "URG_do", "heartbeat_do"]

        # get rid of useless columns
        attacks.drop(useless, axis=1, inplace=True)

        # remove socket information (first 4 elements)
        attacks_final = attacks.drop(columns=["Uplink_IP", "Downlink_IP", "Uplink_port", "Downlink_port"])

        # cast from string to float
        attacks_final = attacks_final.astype(np.float64)

        # Normalize dataset
        attacks_final = pd.DataFrame(self.scaler.transform(attacks_final.iloc[:, :]))

        time_end = int(round(time.time() * 1000))
        time_required = time_end - time_start

        detect = self.__detect_attacks(attacks_final, self.um, 36, self.k)

        prediction = detect['Prediction'].tolist()
        return [parsers[i].addNewFeatures(bool(prediction[i]), str(time_required))
                for i in range(0, len(prediction))]

    @staticmethod
    # se define la función que construye la matriz de histogramas
    def __histo(datos, centros, desviaciones, n_variables, clusters, W):
        H = np.zeros((len(datos), clusters))
        Weight = 1 / n_variables
        for k in range(len(datos)):
            inpt = datos.iloc[k]
            for i in range(clusters):
                for j in range(n_variables):
                    sup = centros[i][j] + W * desviaciones[j]
                    inf = centros[i][j] - W * desviaciones[j]
                    feat = np.array(inpt[j])
                    if (feat < sup and feat > inf):
                        H[k, i] += Weight

        return H

    def __detect_attacks(self, attacks, thresh, var, clusters):
        probs = pd.DataFrame(np.exp(self.gmm.score_samples(attacks.iloc[:, 0:var])), columns=["Probability"])
        probs["Prediction"] = 'D'
        probs.loc[probs.Probability == 0, "Prediction"] = 'A'
        probs.loc[probs.Probability >= 1, "Prediction"] = 'N'
        probs.loc[probs.Prediction == 'A', "Prediction"] = 1.0
        probs.loc[probs.Prediction == 'N', "Prediction"] = 0.0
        probs.loc[probs.Prediction == 'D', "Prediction"] = 0.5

        print(probs)

        His_t = MLModel.__histo(attacks, self.centers, self.std, var, clusters, 1)
        dif = self.nn.predict(His_t) - His_t
        REC_t = np.sqrt(np.sum(dif ** 2, axis=1))
        sug = []

        for i in range(len(REC_t)):
            if REC_t[i] < thresh:
                sug.append(0)
            else:
                sug.append(1)

        probs["Sugerencia"] = sug

        print(probs)

        probs.loc[probs.Prediction == 0.5, 'Prediction'] = probs.loc[probs.Prediction == 0.5, 'Sugerencia']

        print(probs)

        return probs
