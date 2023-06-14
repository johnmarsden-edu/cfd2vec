from torch_geometric.data import Data, EdgeAttr
from torch_geometric.data.graph_store import EdgeLayout
from torch_geometric.utils import index_sort
from torch_geometric.utils.sparse import index2ptr, ptr2index
import copy

def my_edge_to_layout(data: Data, attr: EdgeAttr, layout: EdgeLayout, store: bool = False):
    (row, col), perm = data.get_edge_index(attr), None

    if layout == EdgeLayout.COO:  # COO output requested:
        if attr.layout == EdgeLayout.CSR:  # CSR->COO
            row = ptr2index(row)
        elif attr.layout == EdgeLayout.CSC:  # CSC->COO
            col = ptr2index(col)

    elif layout == EdgeLayout.CSR:  # CSR output requested:
        if attr.layout == EdgeLayout.CSC:  # CSC->COO
            col = ptr2index(col)

        if attr.layout != EdgeLayout.CSR:  # COO->CSR
            if attr.size: 
                num_rows = attr.size[0]
            elif data.num_nodes:
                num_rows = data.num_nodes
            else:
                num_rows = int(row.max()) + 1
            
            row, perm = index_sort(row, max_value=num_rows)
            col = col[perm]
            row = index2ptr(row, num_rows)
    
    # CSC output requested:
    elif attr.layout == EdgeLayout.CSR:  # CSR->COO
            row = ptr2index(row)

    elif attr.layout != EdgeLayout.CSC:  # COO->CSC
        num_cols = attr.size[1] if attr.size else int(col.max()) + 1
        if not attr.is_sorted:  # Not sorted by destination.
            col, perm = index_sort(col, max_value=num_cols)
            row = row[perm]
        col = index2ptr(col, num_cols)

    if attr.layout != layout and store:
        attr = copy.copy(attr)
        attr.layout = layout
        if perm is not None:
            attr.is_sorted = False
        data.put_edge_index((row, col), attr)

    return row, col, perm

Data._edge_to_layout = my_edge_to_layout