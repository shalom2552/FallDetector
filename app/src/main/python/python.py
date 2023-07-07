import numpy as np


def count_peaks(y, threshold=0):
    # Convert the input arrays to numpy arrays
    y = np.array(y)

    # Find the indices where the gradient changes from positive to negative
    gradient = np.gradient(y)
    change_points = np.where((gradient[:-1] > 0) & (gradient[1:] < 0))[0] + 1

    # Count the peaks that are above the given threshold
    peak_count = len([i for i in change_points if y[i] > threshold])

    return peak_count


def main(data, threshold):
    records = []
    if data.size() == 0:
        return 0
    for i in range(data.size()):
        records.append(data.get(i))

    return count_peaks(records, threshold)



# 107
# 110
# 102
# 98
# 103

# 148
# 155
# 140
# 148
# 145

