# The program reads a file containing strings that describe how to
# construct a 'tf * idf / ln' formula and writes out Lucene-5.4.0 Java
# classes that incorporate that particular weighting formula.
#
# USAGE : awk -f sss-sss.awk sss-sss.parts TMPL.java
#
# INPUT : The files sss-sss.parts and TMPL.java (or TMPLe.java)
#
# OUTPUT: a) A file sss-sss.formulae, that contain the formulae as list
#            for reference.
#         b) The Java class files are placed here ./classes/*.java
#
# TMPL.java (and TMPLe.java) acts as a template. The program
# constructs the formula (a string) and the Java class name, and based
# on queues in the template, places the two strings at the appropriate
# positions using gsub(). There is no fancy code-generation going on
# here.
#
# The ones I don't know how to implement in Lucene-5.4.0 is marked as
# OFF in sss-sss.parts
#
# WARN: The 'classes' directory needs to be created beforehand.
#
# The Java classes have to be moved to LTR/src/ and LTR rebuilt (using
# 'ant' from inside the LTR directory) to make them available at
# run-time.

function printa(a) {
    for (i in a)
	printf("%s %s\n", i, a[i])
}

BEGIN {
    
    i_f   = ARGV[1]
    i_f1  = ARGV[2]
    o_f   = "sss-sss.formulae"
    intf  = 0
    inidf = 0
    inln  = 0
    while (getline <i_f) {
	n = split($0, s, ":")
	# print n;continue;
	if (n == 1)
	    t = s[1]
	else if (n == 2) {
	    if (t == "TF")
		TF[s[1]] = s[2]
	    if (t == "IDF")
		IDF[s[1]] = s[2]
	    if (t == "LN")
		LN[s[1]] = s[2]
	}
    }

    # printa(TF)
    # printf("\n")
    # printa(IDF)
    # printf("\n")
    # printa(LN)
    # exit(0)
    
    x = 1

    fmt_pretty = "%2d %s%s%s (%-24s) * (%-30s) / (%-13s)\n"
    fmt_idf    = "idf = %s;"
    fmt_idf1   = "idf += %s;"
    fmt_ln     = "K = %s;"
    fmt_w      = "w = %s / K * idf;"
    
    for (i in TF) {
	for (j in IDF) {
	    for (k in LN) {
		
		printf(fmt_pretty, x++,
		       i, j, k,
		       TF[i], IDF[j], LN[k]) >o_f

		class     = toupper(i j k)
		line_idf  = sprintf(fmt_idf, IDF[j])
		line_idf1 = sprintf(fmt_idf1, IDF[j])
		line_ln   = sprintf(fmt_ln, LN[k])
		line_w    = sprintf(fmt_w, TF[i])

		# printf("%s %s\n", class, line_w)

		fi = i_f1
		fo = "classes/" class ".java"
		if (fi ~ /.*[e]\.java/)
		    fo = "classes/" class "e.java"

		while(getline l<fi) {
		    gsub("TMPL", class, l)
		    gsub("idf = log\\(N\\/n\\);", line_idf, l)
		    gsub("idf \\+= log\\(N\\/n\\);", line_idf1, l)
		    gsub("K = 1\\.0f;", line_ln, l)
		    gsub("w = 1\\.0f;", line_w, l)
		    print l>fo
		}

		close(fi)
		close(fo)
	    }
	    printf("\n") >o_f
	}
    }
}
