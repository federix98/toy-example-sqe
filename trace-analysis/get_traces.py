import time, requests
import statistics, numpy as np
import json

if __name__ == "__main__":

    LIMIT = 500                       # iterations run by k6

    end = int(time.time()) 
    start = end - 24 * 3600    # 24 hours ago
    TEMPO_EP = "http://localhost:3200/api/search"
    r = requests.get(
        TEMPO_EP, 
        params={ 
            "q": '{ name = "SELECT orders" }', 
            "start": start, 
            "end": end, 
            "limit": LIMIT, 
            "spss": 1 # num of spans per span set
        }
    ).json()

    print(f"Got {len(r['traces'])} traces")

    d = [
        int(sp["durationNanos"]) / 1e6
        for t in r.get("traces", [])
        for ss in t.get("spanSets", [])
        for sp in ss["spans"]
    ]

    print(f"Duration of spans (ms): {d}")

    if not d:
        raise SystemExit("No spans returned: check the query, the time window and the Tempo URL")

    print(f"spans returned: {len(d)}, expected: {LIMIT}")
    print(f"average: {statistics.mean(d):.2f} ms")
    print(f"p95:     {np.percentile(d, 95):.2f} ms")

    # count the number of outliers with IQR method
    q1 = np.percentile(d, 25)
    q3 = np.percentile(d, 75)
    iqr = q3 - q1
    lower_bound = q1 - 1.5 * iqr
    upper_bound = q3 + 1.5 * iqr

    print(f"lower bound: {lower_bound:.2f} ms")
    print(f"upper bound: {upper_bound:.2f} ms")

    outliers = [x for x in d if x < lower_bound or x > upper_bound]
    print(f"outliers: {len(outliers)} over {len(d)} spans, {len(outliers)/len(d)*100:.2f}%")