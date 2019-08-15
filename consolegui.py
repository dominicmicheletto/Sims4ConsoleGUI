import sims4.log
import sims4.reload
import sims4.commands
import services
from sims.sim_info_types import Gender, Species, SpeciesExtended
from sims.sim import Sim

import socket
from functools import wraps
import os
import subprocess
import sys
import threading
import atexit


HOST = '127.0.0.1'
PORT = 914
path = r"C:\Users\miche\OneDrive\Documents\NetBeansProjects\ConsoleGUI\dist\ConsoleGUI.jar"
CURRENT_DIRECTORY = os.path.abspath(os.path.join(os.path.dirname(os.path.realpath(__file__)), os.pardir))


def detect_mcc() -> tuple:
    try:
        mc_settings = __import__('mc_settings')
        mcc_settings = mc_settings.Settings()

        toggle_settings, mcc_loaded = {
                                          'move_objects': mcc_settings.get_move_objects_enabled(),
                                          'hidden_objects': mcc_settings.get_debug_objects_enabled(),
                                          'testing_cheats': mcc_settings.get_testing_cheats_enabled(),
                                          'game_play_unlocks': mcc_settings.get_ignore_unlocks_enabled(),
                                          'hover_effects': mcc_settings.get_hover_effects_enabled(),
                                          'headline_effects': mcc_settings.get_headline_effects_enabled(),
                                          'full_edit_mode': mcc_settings.get_full_edit_cas()
                                      }, True
    except ImportError:
        toggle_settings, mcc_loaded = dict(), False

    return toggle_settings, mcc_loaded


def detect_tm() -> tuple:
    unlocks_list = {
        'moo': ('move_objects', False),
        'testing': ('testing_cheats', False),
        'fulleditmode': ('full_edit_mode', False)
    }
    toggles = {
        'move_objects': False,
        'testing_cheats': False,
        'full_edit_mode': False,
    }
    tmex_loaded = False

    for (key, value) in unlocks_list.items():
        name = "tmex-always{}".format(key)
        try:
            module = __import__(name)

            unlocks_list[key] = (value[0], True)
            toggles[value[0]] = True
            tmex_loaded = True

            del module
        except ImportError:
            pass

    return toggles, tmex_loaded


def inject_to(target_object, target_function_name):
    def inject(target_function, new_function):
        @wraps(target_function)
        def _inject(*args, **kwargs):
            return new_function(target_function, *args, **kwargs)

        return _inject

    def _inject_to(new_function):
        target_function = getattr(target_object, target_function_name)
        setattr(target_object, target_function_name, inject(target_function, new_function))
        return new_function

    return _inject_to


@sims4.commands.Command('reload', command_type=sims4.commands.CommandType.Live)
def reload_console_gui(_connection=None):
    import sims4.reload as r
    output = sims4.commands.CheatOutput(_connection)

    global _run_thread
    _run_thread = False

    try:
        dirname = os.path.dirname(os.path.realpath(__file__))

        filename = os.path.join(dirname, "consolegui") + ".py"

        output("Reloading {}".format(filename))
        reloaded_module = r.reload_file(filename)

        if reloaded_module is not None:
            output("Done reloading!")
        else:
            output("Error loading module or module does not exist")

    except BaseException as e:
        output("Reload failed: ")
        for v in e.args:
            output(v)


with sims4.reload.protected(globals()):
    log = sims4.log.Logger("ConsoleGUI")

    _open = False
    _process = None
    _thread = None
    _run_thread = False


def get_command_from_input(cmd: str):
    import re
    raw_command = cmd.replace("COMMAND: ", "").replace("\n", "")
    cmd_map = {
        'motherlode': '|motherlode',
        'rosebud': '|rosebud',
        'kaching': '|kaching',
        'freebuild': 'bb.enablefreebuild'
    }
    cmd_list = [
        (re.compile("^money [0-9]*$"), True),
        (re.compile("^sims.modify_funds [-]*[0-9]*$"), True),
        (re.compile("^stats.set_skill_level [a-zA-Z_]* [0-9]*"), True)
    ]

    

    if raw_command in cmd_map:
        return cmd_map[raw_command]
    else:
        for (command, uses_pipe) in cmd_list:
            if command.match(raw_command):
                log.info("{}{}".format("|" if uses_pipe else "", raw_command))
                return "{}{}".format("|" if uses_pipe else "", raw_command)
        return raw_command


def get_relatives(sim):
    relatives = list()

    for child in sim.get_all_children_recursive_gen():
        relatives += get_relatives(child)
        relatives.append(child.full_name)

    return relatives


def loop(_connection=None):
    sims_list = {sim.sim_id: sim for sim in services.sim_info_manager().get_all()}
    update_list = list()

    try:
        @inject_to(Sim, 'on_add')
        def sim_added(original, self):
            nonlocal update_list

            original(self)
            update_list.append((self, "Added"))
            # sims[self.sim_id] = self

            sims4.log.Logger("ConsoleGUI_INFO").info("{}", update_list[-1])

        @inject_to(Sim, 'on_remove')
        def sim_removed(original, self):
            nonlocal update_list

            original(self)
            update_list.append((self, "Removed"))
            # del sims[self.sim_id]

            sims4.log.Logger("ConsoleGUI_INFO").info("{}", update_list[-1])

        """
        @inject_to(Sim.full_name, 'fget')
        def sim_renamed(original, self):
            nonlocal update_list

            original(self)
            update_list.append((self, "Changed"))

            sims4.log.Logger("ConsoleGUI_INFO").info("{}", update_list[-1])
        """

        def main_loop():
            global _thread, _run_thread
            nonlocal update_list

            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as echo:
                echo.connect((HOST, PORT))
                echo.send("MESSAGE: Connect\n".encode())

                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                    data: str = ""

                    def callback():
                        out = ''

                        log.info("HERE")
                        @inject_to(sims4.commands.Output, '__call__')
                        def output_write(_, self, s):
                            nonlocal out

                            _(self, s)
                            out = s
                            log.info(s)
                            return sims4.commands.cheat_output(s, self._context)

                        @inject_to(sims4.commands, 'output')
                        def output_write2(_, s, context):
                            nonlocal out

                            _(s, context)
                            out = s
                            log.info(s)
                            return sims4.commands.cheat_output(s, context)

                        return out

                    def process_command():
                        nonlocal data, update_list

                        data = sock.recv(1024).decode()
                        if data.startswith("COMMAND: "):
                            sims4.commands.client_cheat(get_command_from_input(data), _connection)
                            output = callback()
                            if output:
                                sock.send("OUTPUT: {}\n".format(output.replace("\n", "\t")).encode())
                            else:
                                sock.send("OUTPUT: {}\n".format("NO-OP").encode())
                        elif data.startswith("QUERY: "):
                            if data == "QUERY: HOUSEHOLD FUNDS\n":
                                client = services.client_manager().get_first_client()
                                funds = client.household.funds.money
                                sock.send("MESSAGE: INFO: FUNDS: {}\n".format(funds).encode())
                            elif data == "QUERY: POPULATION CHANGES\n":
                                sock.send("MESSAGE: INFO: POPULATION CHANGES: {}\n".format(len(update_list)).encode())
                                for (sim, change) in update_list:
                                    sock.send("MESSAGE: INFO: SIM: {}\0{}\0{}\0{}\n".format(change,
                                              sim.full_name, sim.sim_id, sim.species == Species.HUMAN).encode())
                                update_list.clear()
                            elif "QUERY: RELATIONSHIP BETWEEN" in data:
                                global log

                                query = data.replace("QUERY: RELATIONSHIP BETWEEN ", "").replace("\n", "").split('\0')
                                ids, rel_type, is_human = query[0].split(','), query[1] == 'true', query[2] == 'true'

                                from relationships import relationship_track
                                if rel_type and is_human:
                                    track = relationship_track.DEFAULT
                                elif rel_type and not is_human:
                                    track = relationship_track.RelationshipTrack.SIM_TO_PET_FRIENDSHIP_TRACK
                                elif not rel_type and is_human:
                                    track = relationship_track.RelationshipTrack.ROMANCE_TRACK
                                else:
                                    track = relationship_track.DEFAULT

                                relationship_service = services.relationship_service()
                                value = relationship_service.get_relationship_score(*(int(_id) for _id in ids))
                                log2 = sims4.log.Logger("ConsoleGUI_Relationship")
                                log2.info("Query: {}, Sims: {}, track: {}, value: {}",
                                          query,
                                          (sims_list[int(ids[0])], sims_list[int(ids[1])]),
                                          track,
                                          relationship_service.get_relationship_score(*(int(_id) for _id in ids), track))

                                sock.send("MESSAGE: INFO: RELATIONSHIP VALUE: {}\n".format(int(value)).encode())

                    sock.connect((HOST, PORT))
                    sock.send("MESSAGE: Ready\n".encode())

                    sock.send("MESSAGE: INFO: SIMS LIST: {}\n".format(len(sims_list)).encode())
                    for sim in sims_list.values():
                        if sim.species == Species.HUMAN:
                            species = "HUMAN"
                        elif sim.species == Species.CAT:
                            species = "CAT"
                        elif sim.species == Species.DOG:
                            species = "DOG"
                        else:
                            species = "INVALID"

                        sock.send("MESSAGE: INFO: SIM: {}\0{}\0{}\0{}\n".format(
                            sim.full_name, sim.sim_id, str(sim.species == Species.HUMAN).lower(), species).encode())

                    def close_on_exit(_socket):
                        _socket.send("MESSAGE: Request Close\n".encode())
                    atexit.register(close_on_exit, sock)

                    mccc = detect_mcc()
                    tmex = detect_tm()

                    if mccc[1]:
                        bit_vector = 0
                        for (index, value) in enumerate(mccc[0].values()):
                            bit_vector |= int(value) << index

                        sock.send("MESSAGE: INFO: MCCC DETECTED: True\n".encode())
                        sock.send("MESSAGE: INFO: {}\n".format(bit_vector).encode())
                    else:
                        sock.send("MESSAGE: INFO: MCCC DETECTED: False\n".encode())

                    if tmex[1]:
                        bit_vector = 0
                        for (index, value) in enumerate(tmex[0].values()):
                            bit_vector |= int(value) << index

                        sock.send("MESSAGE: INFO: TMEX DETECTED: True\n".encode())
                        sock.send("MESSAGE: INFO: {}\n".format(bit_vector).encode())
                    else:
                        sock.send("MESSAGE: INFO: TMEX DETECTED: False\n".encode())

                    process_command()

                    while data != b'MESSAGE: Closing\n' and data and _run_thread:
                        process_command()

                    sock.close()
                    echo.close()
                    _thread = None
                    return

        main_loop()
        sims4.commands.Output(_connection)("Thread closed")
    except:
        (exc_type, exc, exc_tb) = sys.exc_info()
        sims4.log.exception('ConsoleGUI', 'Exception: ', exc=exc,
                            owner='LeRoiDeTout')


@sims4.commands.Command("console.start", command_type=sims4.commands.CommandType.Live)
def start_console(_connection=None):
    global _open, _process, _thread, _run_thread

    _thread = threading.Thread(args=(_connection, ), target=loop)
    _thread.start()
    _run_thread = True
