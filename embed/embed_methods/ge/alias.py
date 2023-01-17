import numpy as np


# def create_alias_table(area_ratio: list[float]):
#     """

#     :param area_ratio: sum(area_ratio)=1
#     :return: accept,alias
#     """
#     l = len(area_ratio)
#     accept: list[int]
#     alias: list[int]
#     accept, alias = [0] * l, [0] * l

#     small: list[int]
#     large: list[int]
#     small, large = [], []
#     area_ratio_ = np.array(area_ratio) * l
#     for i, prob in enumerate(area_ratio_):
#         if prob < 1.0:
#             small.append(i)
#         else:
#             large.append(i)

#     while small and large:
#         small_idx, large_idx = small.pop(), large.pop()
#         accept[small_idx] = area_ratio_[small_idx]
#         alias[small_idx] = large_idx
#         area_ratio_[large_idx] = area_ratio_[large_idx] - (1 - area_ratio_[small_idx])
#         if area_ratio_[large_idx] < 1.0:
#             small.append(large_idx)
#         else:
#             large.append(large_idx)

#     while large:
#         large_idx = large.pop()
#         accept[large_idx] = 1
#     while small:
#         small_idx = small.pop()
#         accept[small_idx] = 1

#     return accept, alias


# def alias_sample(accept: list[float], alias: list[int]):
#     """

#     :param accept:
#     :param alias:
#     :return: sample index
#     """
#     N: int = len(accept)
#     i: int = int(np.random.random()*N)
#     r: float = np.random.random()
#     if r < accept[i]:
#         return i
#     else:
#         return alias[i]
