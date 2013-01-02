package FileReaders;


public class FileReaders implements Consts{
	public FileReaders (){

		Instance i1=new Instance("hg19");
		System.out.println(i1.get_Annotations());
		System.out.println(System.currentTimeMillis());
		System.out.println(i1.get_Assemblies());
		System.out.println(System.currentTimeMillis());
		System.out.println(i1.get_Annotations());
		System.out.println(System.currentTimeMillis());
		System.out.println(i1.update("chr1",33031597,33041570,1350));
		System.out.println(System.currentTimeMillis());
		String[] externals={"microSatellite"};
		String[] externals_links={"http://202.118.228.68/gbfiles/MicroSatellite.hg19.sorted.bed.gz"};
		String[] externals_types={"BEDGZ"};
		String[] externals_modes={MODE_DENSE};
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
		externals[0]="microSatellite";
		i1.remove_Tracks(externals);
		System.out.println(System.currentTimeMillis());
		externals[0]="cytoband";externals_links[0]="input/cytoBand.hg19.txt";externals_types[0]="CYTO";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
/*		externals[0]="BamTest";externals_links[0]="http://202.118.228.68/gbfiles/MCF7_DNAseq.hg19.sorted.bam";externals_types[0]="BAM";externals_modes[0]=MODE_DETAIL;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
*/		externals[0]="GffTest";externals_links[0]="http://127.0.0.1/gbfiles/refGene.hg19.sorted.gtf.gz";externals_types[0]="GFF";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
		i1.remove_Tracks(externals);
		externals[0]="GtfTest";externals_links[0]="http://127.0.0.1/gbfiles/refGene.hg19.sorted.gtf.gz";externals_types[0]="GTF";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
/*		externals[0]="GvfTest";externals_links[0]="http://127.0.0.1/gbfiles/Venter.sorted.gvf.gz";externals_types[0]="GVF";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
/*		externals[0]="bbTest";externals_links[0]="http://127.0.0.1/gbfiles/ensGene.hg19.bb";externals_types[0]="BB";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
*//*		externals[0]="bwTest";externals_links[0]="http://127.0.0.1/gbfiles/chr21.phyloP46way.placental.bigwig";externals_types[0]="BW";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
/*		externals[0]="wigTest";externals_links[0]="/var/www/gbfiles/chr21.phyloP46way.placental.wigFix";externals_types[0]="WIG";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
		externals[0]="bgTest";externals_links[0]="/var/www/gbfiles/chr21.phyloP46way.placental.bedGraph";externals_types[0]="BG";externals_modes[0]=MODE_DENSE;
		i1.add_Externals(externals,externals_links,externals_types,externals_modes);
		System.out.println(i1.add_Tracks(externals,externals_modes));
		System.out.println(System.currentTimeMillis());
/*		System.out.println(i1.update("chr2", 24754110, 24855430,1000));
		System.out.println(System.currentTimeMillis());
*/	}
}