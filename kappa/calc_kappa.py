from __future__ import annotations
import click
from typing import Callable
from csv import DictReader
import numpy as np
import numpy.typing as npt


def probability_observed(coder_tables: npt.NDArray[np.float64], t_norm: Callable[..., npt.NDArray[np.float64]]) -> float:
    
    # Our t-norm is going to be the min t-norm
    # Axis 0: Units, Axis 1: Categories
    t_norm_applied = t_norm(coder_tables, axis=0)

    # Then we calculate the sum of the categories
    # Axis 0: Units
    summed_categories = np.sum(t_norm_applied, axis=1)

    # Then we calculate the mean
    mean_units = np.mean(summed_categories)
    return mean_units



def probability_expected(coder_tables: npt.NDArray[np.float64], t_norm: Callable[..., npt.NDArray[np.float64]]) -> float:
    '''
    
    coder_tables - Axis 0: Coders, Axis 1: Units, Axis 2: Categories
    '''
    t_norm_multipliers = t_norm([[0, 0], [0, 1], [1, 0], [1, 1]], axis=1)

    # Calculate the mean probability that a coder chose a specific category for any given unit
    prob_coder_chose_category = np.mean(coder_tables, axis=1)

    prob_both_coders_chose_category = prob_coder_chose_category[0] * prob_coder_chose_category[1]

    all_probs_mul_by_t_norm = np.multiply.outer(t_norm_multipliers, prob_both_coders_chose_category)

    sum_probs = np.sum(all_probs_mul_by_t_norm, axis=0)
    return np.sum(sum_probs)


def fuzzy_kappa(coder_tables: npt.NDArray[np.float64], t_norm: Callable[..., npt.NDArray[np.float64]]) -> tuple[float, float, float]:
    '''
    coder_tables - Axis 0: Coders, Axis 1: Units, Axis 2: Categories

    P^0 = 1 / N_u * (sum from i = 1 to N_u of the sum from j = 1 to N_e of the t-norm of mu_j^{r_1}(u_i) and mu_j^{r_2}(u_i))
    '''
    p_o = probability_observed(coder_tables, t_norm)
    p_e = probability_expected(coder_tables, t_norm)
    return (p_o, p_e, (p_o - p_e) / (1 - p_e))


def tag_files(tag_file: click.File, tags: set[str]) -> dict[str, set[str]]:
    click.echo(f'Reading the tagged files from {click.format_filename(tag_file.name)}')
    tag_file_1_split: list[str] = tag_file.read().split('\n')
    tagged_files: dict[str, set[str]] = {}

    for tagged_file in DictReader(tag_file_1_split):
        tag = tagged_file['tag'].lower().strip()
        if tag not in tags:
            continue

        if tagged_file['document'] not in tagged_files:
            tagged_files[tagged_file['document']] = set()
        tagged_files[tagged_file['document']].add(tag)

    return tagged_files


@click.command()
@click.argument('tags', type=click.File('r'))
@click.argument('tag_file_1', type=click.File('r'))
@click.argument('tag_file_2', type=click.File('r'))
def calc(tags: click.File, tag_file_1: click.File, tag_file_2: click.File):
    '''Calculates the Fuzzy Kappa given a CSV of TAGS and two CSVs of tagged data TAG_FILE_1 and TAG_FILE_2
    
    TAGS - A CSV with the tags that you are going to be calculating the kappa of for the two tag files given

    TAG_FILE_1 - A CSV with the first taggers tagged files in the taguette all highlights view exported format
    
    TAG_FILE_2 - A CSV with the second taggers tagged files in the taguette all highlights view exported format
    '''
    click.echo(f'Pulling the tags from {click.format_filename(tags.name)}')
    tags_split: list[str] = tags.read().split('\n')
    tag_set: set[str] = {tag['tag'].lower().strip() for tag in DictReader(tags_split)}

    tagged_files_1 = tag_files(tag_file_1, tag_set)

    tagged_files_2 = tag_files(tag_file_2, tag_set)

    shared_files = set(tagged_files_1.keys()).intersection(set(tagged_files_2.keys()))

    if len(shared_files) < 2:
        click.secho('You must have at least 2 shared files to calculate a kappa', err=True, fg='red')
        return
        

    click.echo(f'Calculating the fuzzy kappa for the {len(shared_files)} shared files')

    units_1: list[list[float]] = []
    units_2: list[list[float]] = []
    for sf in shared_files:
        sf_tags_1 = tagged_files_1[sf]
        sf_tags_2 = tagged_files_2[sf]
        cat_1: list[float] = []
        cat_2: list[float] = []

        cat_1_v = 1 / len(sf_tags_1)
        cat_2_v = 1 / len(sf_tags_2)
        for tag in tag_set:
            cat_1.append(cat_1_v if tag in sf_tags_1 else 0)
            cat_2.append(cat_2_v if tag in sf_tags_2 else 0)

        units_1.append(cat_1)
        units_2.append(cat_2)

    coder_table = np.array([units_1, units_2])
    p_o, p_e, kappa = fuzzy_kappa(coder_table, np.min)
    click.echo(f'The probability that the coders were in agreement observed is {p_o}')
    click.echo(f'The probability that the coders were expected to be in agreement based on the distribution of tags is {p_e}')
    click.echo(f'Thus, the fuzzy kappa is {kappa}')


if __name__ == '__main__':
    calc()