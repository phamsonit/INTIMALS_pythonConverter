def load(filename, dictionnary):        # NE PAS EFFACER CETTE LIGNE
    """
    @pre    filename est le nom d'un fichier de texte,
            dictionnary est un dictionnaire avec :
                comme clés : des chaînes de caractères (les noms d'utilisateurs)
                comme valeurs : des entiers (un code pin associé à l'utilisateur)
    @post   Met à jour le dictionnaire à partir des clients repris dans le fichier de nom filename.
            Si une erreur se produit pendant la lecture du fichier, le dictionnaire n'est pas
            modifié.
            Retourne :
                True     si le fichier a été lu sans erreurs
                False    sinon
    """
    try:
        with open(filename) as file:
            ready=[]
            for line in file:
                stripped=line.strip()
                splitted=stripped.split("!")
                ready.append(splitted)
                if len(splitted)!=2:
                    return False
                if len(splitted[1])!=4:
                    return False
                if splitted[1][0]=='0':
                    return False
                try:
                    a=int(splitted[1])
                except:
                    return False
            for tab in ready:
                dictionnary[tab[0]]=int(tab[1])
            return True
    except OSError as error:
        return False
    ### VOTRE REPONSE
