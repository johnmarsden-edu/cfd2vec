# -*- coding: utf-8 -*-

"""Pull list of repos from org
This script pulls all repositories from an organization and
writes them to a text file
This is a standalone script and should not be imported.
    * pull_repos - main function of script
    * __get_repositories - pulls repositories from Github enterprise server
    * __write_to_file - writes list of repos to file
"""

import getpass
from typing import Any, NamedTuple
import requests
from requests.auth import HTTPBasicAuth
import sys
from pathlib import Path
from git.repo import Repo
from git import Commit
import pandas as pd
import tempfile
import itertools
import shutil


def pull_repos(org: str) -> tuple[str, list[str]]:
    """Handle pulling repos and writing them to file
    This gets a list of repo jsons, pulls the list of names, inserts
    the organization name at the top, and writes to the out_file.
    Parameters
    ----------
    org: str
        Github Enterprize organization
    out_file: str
        Name of file to output data to
    """
    repo_jsons = __get_repositories(org)
    repos = [str(repo['name']) for repo in repo_jsons if 'name' in repo]
    return (org, repos)


def __get_repositories(org: str):
    """Make API request
    First obtain username and password from user, then make Github
    Enterprise server API request. Since there is a max of 100 repos
    per page, we iterate until no the response is empty.
    Parameters
    ----------
    org: str
        Github Enterprize organization
    Returns
    ----------
    ret: list
        List of dict values that contains information about repos
    """
    print('Input Github Enterprise username and password.')
    username = input('Username: ')
    password = getpass.getpass('Password: ')
    url = f'https://github.ncsu.edu/api/v3/orgs/{org}/repos'

    ret: list[dict[Any, Any]] = []
    page: int = 1
    print('Fetching all pages, this may take some time.')
    while True:
        res = requests.get(
            url,
            auth=HTTPBasicAuth(username, password),
            params={
                'page': page,
                'per_page': 100
            }
        )
        if res.status_code == 200:
            res_json = res.json()
            if res_json:
                ret.extend(res_json)
            else:
                print('No more pages found.')
                break
        else:
            print('Unable to reach server. Try running the script again.')
            sys.exit()
        page += 1
    return ret



# package imports
class NCSURepo(NamedTuple):
    course: str
    section: str
    assignment: str
    group_id: str
    name: str


def walk_through_files(path: Path, file_extension: str = '.java'):
    for filepath in path.rglob(f'*{file_extension}'):
        try:
            yield filepath.resolve(strict=True)
        except:
            pass


cwd = Path.cwd()

AUTHORS_EXCLUDE = {'jessica_schmidt@ncsu.edu', 'jdyoung2@ncsu.edu', 'opala@ncsu.edu'}
def mine_commit(git_repo: Repo, commit: Commit, repo: NCSURepo, repo_path: Path):
    git_repo.git.checkout(commit)
    commit_id = str(commit)
    changed_files = set()
    if commit.parents is not None and len(commit.parents) > 0:
        for diff in commit.diff(commit.parents[0]):
            if diff.a_blob is not None and diff.a_blob.path is not None:
                changed_files.add(diff.a_blob.path)
            if diff.b_blob is not None and diff.b_blob.path is not None:
                changed_files.add(diff.b_blob.path)
    for fname in walk_through_files(repo_path):
        if not fname.is_file():
            continue
        
        with open(fname, 'r', encoding='utf-8', errors='replace') as f:
            f_data = f.read()
            posix_fname = fname.relative_to(repo_path).as_posix()
        data = {
            'commitid': commit_id,
            'repo_name': repo.name,
            'assignment': repo.assignment,
            'file': posix_fname,
            'data': f_data,
            'provided': str(commit.author.email in AUTHORS_EXCLUDE),
            'changed': str(posix_fname in changed_files)
        }
        yield data


def iterate_over_graphs(git_repo: Repo, repo: NCSURepo, repo_path: Path):
    if git_repo.working_tree_dir:
        for commit in git_repo.iter_graphs(reverse=True):
            yield from mine_commit(git_repo, commit, repo, repo_path)


def fetch_repo(repo_name: str, repo_path: Path, org: str) -> Repo:
    print(f' Fetching repo: {repo_name}')

    # if the repo path doesn't exist
    repo_path.mkdir(parents=True, exist_ok=True)

    # fetch repo from Github enterprise
    git_url = f'git@github.ncsu.edu:{org}/{repo_name}.git'
    ssh_cmd = 'ssh -i ~/.ssh/id_ed25519'
    repo = Repo.clone_from(
        git_url,
        repo_path,
        env={'GIT_SSH_COMMAND': ssh_cmd}
    )
    return repo


def create_save_df(save_dir: Path, org: str, assignment: str, files: list[dict[str, str]]):
    # create the dataframe and save it
    df = pd.DataFrame(files)
    df_path = save_dir / f'project_data_{org}_{assignment}.csv'
    df.to_csv(df_path, index=False)


def get_assignment(i: NCSURepo):
    return i.assignment



def iterate_over_repos(repo_list: list[str], org: str, output_dir: Path):
    # iterate over repos in repo_list
    with tempfile.TemporaryDirectory() as tempdir:
        temppath = Path(tempdir)
        print('Iterating over repositories')
        split_repo_names = [(r.split('-'), r) for r in repo_list]
        repos = [NCSURepo(r[0], r[1], r[2], r[3], name) for r, name in split_repo_names if len(r) == 4]
        repos = sorted(repos, key=get_assignment)
        print(f'Loading {len(repos)} repos and their information')
        for assignment, a_repos in itertools.groupby(repos, get_assignment):
            files: list[dict[str, str]] = []
            for repo in a_repos:
                repo_path = temppath / repo.name
                git_repo = fetch_repo(repo.name, repo_path, org)
                files.extend(iterate_over_graphs(git_repo, repo, repo_path))
                # From Brad:
                # usually I use shutil.rmtree(repo_path), but that's broken on windows
                # os.system(f'rm -rf {repo_path}')
                # Comment: I am on Linux so this does not impact me. However, if this
                # impacts some later user, feel free to go back to the original.
                shutil.rmtree(repo_path)

            create_save_df(output_dir, org, assignment, files)


if __name__ == '__main__':
    if len(sys.argv) > 2:
        organization = sys.argv[1]
        output_dir = Path(sys.argv[2])
        org, repo_list = pull_repos(organization)
        iterate_over_repos(repo_list, org, output_dir)
    else:
        print(
            'Include the Github organization and output directory',
            'as a parameter.'
        )